package com.aratiri.infrastructure.nodeoperations;

import com.aratiri.infrastructure.persistence.jpa.entity.NodeOperationStatus;
import com.aratiri.infrastructure.persistence.jpa.entity.NodeOperationEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.NodeOperationType;
import com.aratiri.infrastructure.persistence.jpa.repository.NodeOperationsRepository;
import com.aratiri.payments.application.dto.OnChainPaymentDTOs;
import com.aratiri.payments.application.dto.PayInvoiceRequestDTO;
import com.aratiri.payments.application.port.out.InvoicesPort;
import com.aratiri.payments.application.port.out.LightningNodePort;
import com.aratiri.payments.domain.DecodedInvoice;
import com.aratiri.payments.domain.LightningPayment;
import com.aratiri.payments.domain.LightningPaymentStatus;
import com.aratiri.payments.domain.exception.LightningNodeTransportException;
import com.aratiri.payments.infrastructure.json.JsonUtils;
import com.aratiri.webhooks.application.NodeOperationUnknownOutcomeFacts;
import com.aratiri.webhooks.application.WebhookEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class NodeOperationService {

    private final NodeOperationsRepository nodeOperationsRepository;
    private final LightningNodePort lightningNodePort;
    private final InvoicesPort invoicesPort;
    private final NodeOperationState stateManager;
    private final NodeOperationRetryPolicy retryPolicy;
    private final NodeOperationClaimer claimer;
    private final WebhookEventService webhookEventService;

    @Value("${aratiri.payment.default.fee.limit.sat:200}")
    private int defaultFeeLimitSat;

    @Value("${aratiri.payment.default.timeout.seconds:200}")
    private int defaultTimeoutSeconds;

    @Transactional
    public NodeOperationEntity enqueueLightningPayment(LightningPaymentOperation payment) {
        requireLightningPayment(payment);
        Optional<NodeOperationEntity> existing = nodeOperationsRepository.findByTransactionId(payment.transactionId());
        if (existing.isPresent()) {
            log.info("Node operation already exists for transactionId: {}", payment.transactionId());
            return existing.get();
        }

        String paymentHash = resolvePaymentHash(payment);
        LightningPaymentOperation durablePayment = payment.withPaymentHash(paymentHash);

        NodeOperationEntity operation = NodeOperationEntity.builder()
                .transactionId(payment.transactionId())
                .userId(payment.userId())
                .operationType(NodeOperationType.LIGHTNING_PAYMENT)
                .status(NodeOperationStatus.PENDING)
                .referenceId(paymentHash)
                .requestPayload(JsonUtils.toJson(durablePayment))
                .attemptCount(0)
                .nextAttemptAt(Instant.now())
                .build();

        NodeOperationEntity saved = nodeOperationsRepository.save(operation);
        log.info("Enqueued lightning payment operation: id={}, transactionId={}", saved.getId(), payment.transactionId());
        return saved;
    }

    @Transactional
    public NodeOperationEntity enqueueOnChainSend(OnChainSendOperationFact fact) {
        Optional<NodeOperationEntity> existing = nodeOperationsRepository.findByTransactionId(fact.transactionId());
        if (existing.isPresent()) {
            log.info("Node operation already exists for transactionId: {}", fact.transactionId());
            return existing.get();
        }

        NodeOperationEntity operation = NodeOperationEntity.builder()
                .transactionId(fact.transactionId())
                .userId(fact.userId())
                .operationType(NodeOperationType.ONCHAIN_SEND)
                .status(NodeOperationStatus.PENDING)
                .requestPayload(JsonUtils.toJson(fact.toRequestDto()))
                .attemptCount(0)
                .nextAttemptAt(Instant.now())
                .build();

        NodeOperationEntity saved = nodeOperationsRepository.save(operation);
        log.info("Enqueued on-chain send operation: id={}, transactionId={}", saved.getId(), fact.transactionId());
        return saved;
    }

    public void processOperations() {
        processClaimed("pending", claimer.claimPendingBatch(), this::executeOperation);
        processClaimed("stale", claimer.claimStaleBatch(), this::executeOperation);
        processClaimed("broadcasted", claimer.claimBroadcastedBatch(), this::confirmBroadcastedOperation);
    }

    private void processClaimed(String batchName, List<NodeOperationEntity> operations, OperationHandler handler) {
        for (NodeOperationEntity op : operations) {
            try {
                handler.handle(op);
            } catch (Exception e) {
                log.error("Failed to process {} operation id={}: {}", batchName, op.getId(), e.getMessage(), e);
            }
        }
    }

    void executeOperation(NodeOperationEntity op) {
        if (op.getOperationType() == NodeOperationType.LIGHTNING_PAYMENT) {
            executeLightning(op);
        } else if (op.getOperationType() == NodeOperationType.ONCHAIN_SEND) {
            executeOnChain(op);
        }
    }

    void confirmBroadcastedOperation(NodeOperationEntity op) {
        stateManager.confirmTransaction(op.getTransactionId(), op.getUserId());
        stateManager.markSucceeded(op);
    }

    void executeLightning(NodeOperationEntity op) {
        String paymentHash = op.getReferenceId();

        if (paymentHash != null) {
            PaymentInspection inspection = findPaymentForRetry(op, paymentHash);
            if (!inspection.completed()) {
                return;
            }
            Optional<LightningPayment> existingPayment = inspection.payment();
            if (existingPayment.isPresent()) {
                handleExistingPayment(op, existingPayment.get());
                return;
            }
        }

        if (retryPolicy.shouldFailLightningBeforeSend(op)) {
            stateManager.failTransaction(op.getTransactionId(), "Max attempts reached");
            stateManager.markFailed(op, retryPolicy.lightningMaxAttemptsFailureMessage());
            return;
        }

        executeSendPayment(op);
    }

    private PaymentInspection findPaymentForRetry(NodeOperationEntity op, String paymentHash) {
        try {
            return new PaymentInspection(lightningNodePort.findPayment(paymentHash), true);
        } catch (LightningNodeTransportException e) {
            handleRetryableLightningError(op, "Transport error inspecting payment state", e);
            return new PaymentInspection(Optional.empty(), false);
        } catch (Exception e) {
            handleRetryableLightningError(op, "Exception inspecting payment state", e);
            return new PaymentInspection(Optional.empty(), false);
        }
    }

    private void handleExistingPayment(NodeOperationEntity op, LightningPayment payment) {
        if (payment.status() == LightningPaymentStatus.SUCCEEDED) {
            stateManager.recordFeeIfPresent(op.getTransactionId(), payment);
            stateManager.confirmTransaction(op.getTransactionId(), op.getUserId());
            stateManager.markSucceeded(op);
        } else if (payment.status() == LightningPaymentStatus.IN_FLIGHT) {
            log.info("Payment {} is IN_FLIGHT on LND, scheduling operation retry", op.getReferenceId());
            stateManager.markRetryable(op, "LND payment is IN_FLIGHT");
        } else if (payment.status() == LightningPaymentStatus.FAILED) {
            String reason = payment.failureReason();
            stateManager.failTransaction(op.getTransactionId(), reason);
            stateManager.markFailed(op, "LND reported payment failure: " + reason);
        } else {
            handleUnknownLightningOutcome(op, "LND payment outcome is unknown: " + payment.status());
        }
    }

    private void executeSendPayment(NodeOperationEntity op) {
        PayInvoiceRequestDTO request = normalizeInvoice(paymentRequestFromPayload(op));
        Optional<LightningPayment> finalPayment;
        try {
            finalPayment = lightningNodePort.executeLightningPayment(request, defaultFeeLimitSat, defaultTimeoutSeconds);
        } catch (LightningNodeTransportException e) {
            handleRetryableLightningError(op, "Transport error sending payment", e);
            return;
        } catch (Exception e) {
            handleRetryableLightningError(op, "Exception sending payment", e);
            return;
        }

        if (finalPayment.isEmpty()) {
            handleUnknownLightningOutcome(op, "LND payment returned no terminal result");
            return;
        }

        LightningPayment payment = finalPayment.get();
        if (payment.status() == LightningPaymentStatus.SUCCEEDED) {
            stateManager.recordFeeIfPresent(op.getTransactionId(), payment);
            stateManager.confirmTransaction(op.getTransactionId(), op.getUserId());
            stateManager.markSucceeded(op);
        } else if (payment.status() == LightningPaymentStatus.IN_FLIGHT) {
            stateManager.markRetryable(op, "LND payment is IN_FLIGHT");
        } else if (payment.status() == LightningPaymentStatus.FAILED) {
            String reason = payment.failureReason();
            stateManager.failTransaction(op.getTransactionId(), reason);
            stateManager.markFailed(op, "LND payment failure: " + reason);
        } else {
            handleUnknownLightningOutcome(op, "LND payment outcome is unknown: " + payment.status());
        }
    }

    private void handleUnknownLightningOutcome(NodeOperationEntity op, String message) {
        applyRetryDecision(op, retryPolicy.classifyAmbiguousLightningOutcome(op, message));
    }

    private void handleRetryableLightningError(NodeOperationEntity op, String prefix, Exception e) {
        String message = prefix + ": " + e.getMessage();
        NodeOperationRetryDecision decision = retryPolicy.classifyAmbiguousLightningOutcome(op, message);
        if (decision.unknownOutcome()) {
            log.warn("{} for lightning operation id={} after max attempts", prefix, op.getId(), e);
        } else {
            log.warn("{} for lightning operation id={}, scheduling retry", prefix, op.getId(), e);
        }
        applyRetryDecision(op, decision);
    }

    private void applyRetryDecision(NodeOperationEntity op, NodeOperationRetryDecision decision) {
        if (decision.unknownOutcome()) {
            markUnknownOutcome(op, decision.message());
        } else {
            stateManager.markRetryable(op, decision.message());
        }
    }

    void executeOnChain(NodeOperationEntity op) {
        if (hasBroadcastId(op)) {
            stateManager.confirmTransaction(op.getTransactionId(), op.getUserId());
            stateManager.markSucceeded(op);
            return;
        }

        if (retryPolicy.shouldStopOnChainBeforeSend(op)) {
            markUnknownOutcome(op, retryPolicy.onChainMaxAttemptsUnknownOutcomeMessage());
            return;
        }

        String txid;
        try {
            OnChainPaymentDTOs.SendOnChainRequestDTO request = JsonUtils.fromJson(op.getRequestPayload(), OnChainPaymentDTOs.SendOnChainRequestDTO.class);
            txid = lightningNodePort.sendOnChain(request);
        } catch (Exception e) {
            log.error("On-chain send failed for operation id={}: {}", op.getId(), e.getMessage());
            markUnknownOutcome(op, "Exception sending on-chain transaction: " + e.getMessage());
            return;
        }

        if (txid == null || txid.isBlank()) {
            markUnknownOutcome(op, "LND sendCoins returned no transaction id");
            return;
        }

        op.setExternalId(txid);
        op.setStatus(NodeOperationStatus.BROADCASTED);
        nodeOperationsRepository.save(op);

        stateManager.confirmTransaction(op.getTransactionId(), op.getUserId());
        stateManager.markSucceeded(op);
    }

    private void markUnknownOutcome(NodeOperationEntity op, String error) {
        op.setStatus(NodeOperationStatus.UNKNOWN_OUTCOME);
        op.setLastError(error);
        op.setCompletedAt(Instant.now());
        op.setLockedBy(null);
        op.setLockedUntil(null);
        nodeOperationsRepository.save(op);
        webhookEventService.createNodeOperationUnknownOutcomeEvent(nodeOperationUnknownOutcomeFacts(op));
    }

    private NodeOperationUnknownOutcomeFacts nodeOperationUnknownOutcomeFacts(NodeOperationEntity op) {
        var transaction = stateManager.findTransaction(op.getTransactionId(), op.getUserId()).orElse(null);
        return new NodeOperationUnknownOutcomeFacts(
                op.getId().toString(),
                op.getTransactionId(),
                op.getUserId(),
                op.getOperationType().name(),
                op.getStatus().name(),
                firstPresent(op.getReferenceId(), transaction == null ? null : transaction.getReferenceId()),
                op.getExternalId(),
                op.getAttemptCount(),
                op.getLastError(),
                transaction == null ? null : transaction.getAmountSat(),
                transaction == null || transaction.getStatus() == null ? null : transaction.getStatus().name(),
                transaction == null ? null : transaction.getExternalReference(),
                transaction == null ? null : transaction.getMetadata()
        );
    }

    private String firstPresent(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }

    private boolean hasBroadcastId(NodeOperationEntity op) {
        return op.getExternalId() != null && !op.getExternalId().isBlank();
    }

    private void requireLightningPayment(LightningPaymentOperation payment) {
        if (payment == null) {
            throw new IllegalArgumentException("Lightning payment operation must not be null");
        }
        if (isBlank(payment.transactionId())) {
            throw new IllegalArgumentException("Lightning payment transactionId must not be blank");
        }
        if (isBlank(payment.userId())) {
            throw new IllegalArgumentException("Lightning payment userId must not be blank");
        }
        if (isBlank(payment.invoice())) {
            throw new IllegalArgumentException("Lightning payment invoice must not be blank");
        }
    }

    private String resolvePaymentHash(LightningPaymentOperation payment) {
        if (!isBlank(payment.paymentHash())) {
            return payment.paymentHash();
        }
        try {
            DecodedInvoice decoded = invoicesPort.decodeInvoice(payment.invoice());
            return decoded.paymentHash();
        } catch (Exception e) {
            log.warn("Failed to decode invoice for transactionId: {}, enqueueing without payment hash", payment.transactionId(), e);
            return null;
        }
    }

    private PayInvoiceRequestDTO paymentRequestFromPayload(NodeOperationEntity op) {
        try {
            LightningPaymentOperation payment = JsonUtils.fromJson(op.getRequestPayload(), LightningPaymentOperation.class);
            if (!isBlank(payment.invoice())) {
                return payment.toPaymentRequest();
            }
        } catch (Exception e) {
            log.debug("Lightning operation id={} payload is not a LightningPaymentOperation: {}", op.getId(), e.getMessage());
        }
        return JsonUtils.fromJson(op.getRequestPayload(), PayInvoiceRequestDTO.class);
    }

    private PayInvoiceRequestDTO normalizeInvoice(PayInvoiceRequestDTO request) {
        PayInvoiceRequestDTO normalized = new PayInvoiceRequestDTO();
        if (request.getInvoice() != null && request.getInvoice().toLowerCase().startsWith("lightning:")) {
            normalized.setInvoice(request.getInvoice().substring(10));
        } else {
            normalized.setInvoice(request.getInvoice());
        }
        normalized.setFeeLimitSat(request.getFeeLimitSat());
        normalized.setTimeoutSeconds(request.getTimeoutSeconds());
        return normalized;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @FunctionalInterface
    private interface OperationHandler {
        void handle(NodeOperationEntity operation);
    }

    private record PaymentInspection(Optional<LightningPayment> payment, boolean completed) {
    }
}
