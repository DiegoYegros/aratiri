package com.aratiri.infrastructure.messaging.consumer;

import com.aratiri.infrastructure.persistence.jpa.entity.TransactionEntity;
import com.aratiri.infrastructure.persistence.jpa.repository.TransactionsRepository;
import com.aratiri.payments.application.dto.OnChainPaymentDTOs;
import com.aratiri.payments.application.dto.PayInvoiceRequestDTO;
import com.aratiri.payments.application.event.OnChainPaymentInitiatedEvent;
import com.aratiri.payments.application.event.PaymentInitiatedEvent;
import com.aratiri.payments.application.port.out.InvoicesPort;
import com.aratiri.payments.application.port.out.LightningNodePort;
import com.aratiri.payments.domain.DecodedInvoice;
import com.aratiri.payments.domain.LightningPayment;
import com.aratiri.payments.domain.LightningPaymentStatus;
import com.aratiri.errors.ApplicationException;
import com.aratiri.transactions.application.dto.TransactionDTOResponse;
import com.aratiri.transactions.application.dto.TransactionStatus;
import com.aratiri.transactions.application.port.in.TransactionsPort;
import com.aratiri.webhooks.application.NodeOperationUnknownOutcomeFacts;
import com.aratiri.webhooks.application.dto.WebhookPayloadData;
import com.aratiri.webhooks.application.port.out.WebhookEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Executes payment side effects on LND from Kafka consumer threads. Before ANY LND interaction the
 * service atomically claims the transaction row ({@code current_status} PENDING→EXECUTING) so that
 * exactly one delivery owns the execution even under concurrent redelivery; losers skip without
 * touching LND. A failed execution releases the claim so the retry-topic redelivery can retry; a
 * crashed owner leaves the claim behind and {@code TransactionReconciliationJob} reclaims it past
 * min-age via LND checks. The on-chain label lookup additionally prevents double broadcasts after
 * a crash between LND sendCoins and the local confirmation.
 */
@Service
@Slf4j
public class PaymentExecutionService {

    public static final String ON_CHAIN_LABEL_PREFIX = "aratiri-onchain-";
    static final int UNKNOWN_OUTCOME_ATTEMPT_COUNT = 5;

    private final TransactionsRepository transactionsRepository;
    private final LightningNodePort lightningNodePort;
    private final InvoicesPort invoicesPort;
    private final TransactionsPort transactionsPort;
    private final WebhookEventPublisher webhookEventPublisher;

    @Value("${aratiri.payment.default.fee.limit.sat:200}")
    private int defaultFeeLimitSat;

    @Value("${aratiri.payment.default.timeout.seconds:200}")
    private int defaultTimeoutSeconds;

    public PaymentExecutionService(
            TransactionsRepository transactionsRepository,
            LightningNodePort lightningNodePort,
            InvoicesPort invoicesPort,
            TransactionsPort transactionsPort,
            WebhookEventPublisher webhookEventPublisher
    ) {
        this.transactionsRepository = transactionsRepository;
        this.lightningNodePort = lightningNodePort;
        this.invoicesPort = invoicesPort;
        this.transactionsPort = transactionsPort;
        this.webhookEventPublisher = webhookEventPublisher;
    }

    public static String onChainLabel(String transactionId) {
        return ON_CHAIN_LABEL_PREFIX + transactionId;
    }

    public void executeLightningPayment(PaymentInitiatedEvent event) {
        String transactionId = event.getTransactionId();
        String userId = event.getUserId();
        if (!claimTransaction(transactionId)) {
            skipUnclaimed(transactionId, userId);
            return;
        }
        try {
            executeLightningPaymentClaimed(event, transactionId, userId);
        } catch (RuntimeException e) {
            releaseExecutionClaim(transactionId);
            throw e;
        }
    }

    public void executeOnChainSend(OnChainPaymentInitiatedEvent event) {
        String transactionId = event.getTransactionId();
        String userId = event.getUserId();
        if (!claimTransaction(transactionId)) {
            skipUnclaimed(transactionId, userId);
            return;
        }
        try {
            executeOnChainSendClaimed(event, transactionId, userId);
        } catch (RuntimeException e) {
            releaseExecutionClaim(transactionId);
            throw e;
        }
    }

    public void handleLightningUnknownOutcome(PaymentInitiatedEvent event, String errorMessage, int attemptCount) {
        String transactionId = event.getTransactionId();
        TransactionDTOResponse transaction = loadTransaction(transactionId, event.getUserId()).orElse(null);
        NodeOperationUnknownOutcomeFacts facts = new NodeOperationUnknownOutcomeFacts(
                transactionId,
                transactionId,
                event.getUserId(),
                "LIGHTNING_PAYMENT",
                "UNKNOWN_OUTCOME",
                firstPresent(resolvePaymentHash(event), transaction == null ? null : transaction.getReferenceId()),
                null,
                attemptCount,
                errorMessage,
                transaction == null ? null : transaction.getAmountSat(),
                transaction == null ? null : transaction.getStatus().name(),
                transaction == null ? null : transaction.getExternalReference(),
                transaction == null ? null : transaction.getMetadata()
        );
        publishUnknownOutcomeWebhook(facts);
    }

    public void handleOnChainUnknownOutcome(OnChainPaymentInitiatedEvent event, String errorMessage, int attemptCount) {
        String transactionId = event.getTransactionId();
        TransactionDTOResponse transaction = loadTransaction(transactionId, event.getUserId()).orElse(null);
        NodeOperationUnknownOutcomeFacts facts = new NodeOperationUnknownOutcomeFacts(
                transactionId,
                transactionId,
                event.getUserId(),
                "ONCHAIN_SEND",
                "UNKNOWN_OUTCOME",
                transaction == null ? null : transaction.getReferenceId(),
                null,
                attemptCount,
                errorMessage,
                transaction == null ? null : transaction.getAmountSat(),
                transaction == null ? null : transaction.getStatus().name(),
                transaction == null ? null : transaction.getExternalReference(),
                transaction == null ? null : transaction.getMetadata()
        );
        publishUnknownOutcomeWebhook(facts);
    }

    private void publishUnknownOutcomeWebhook(NodeOperationUnknownOutcomeFacts facts) {
        webhookEventPublisher.publishWebhookEvent(
                "node_operation.unknown_outcome:" + facts.operationId(),
                "node_operation.unknown_outcome",
                "NODE_OPERATION",
                facts.operationId(),
                facts.userId(),
                facts.externalReference(),
                WebhookPayloadData.builder()
                        .operationId(facts.operationId())
                        .transactionId(facts.transactionId())
                        .userId(facts.userId())
                        .operationType(facts.operationType())
                        .operationStatus(facts.operationStatus())
                        .referenceId(facts.referenceId())
                        .externalId(facts.externalId())
                        .attemptCount(facts.attemptCount())
                        .operationError(facts.operationError())
                        .amountSat(facts.amountSat())
                        .status(facts.transactionStatus())
                        .externalReference(facts.externalReference())
                        .metadata(facts.metadata())
                        .build()
        );
    }

    private void executeLightningPaymentClaimed(PaymentInitiatedEvent event, String transactionId, String userId) {
        String paymentHash = resolvePaymentHash(event);
        if (paymentHash != null) {
            Optional<LightningPayment> existing = findPaymentOrThrow(transactionId, paymentHash);
            if (existing.isPresent()) {
                handleLightningPayment(transactionId, userId, paymentHash, existing.get());
                return;
            }
        }

        handleLightningPayment(transactionId, userId, paymentHash, sendPaymentOrThrow(event));
    }

    private void executeOnChainSendClaimed(OnChainPaymentInitiatedEvent event, String transactionId, String userId) {
        String label = onChainLabel(transactionId);
        Optional<String> existingTxId;
        try {
            existingTxId = lightningNodePort.findOnChainTxIdByLabel(label);
        } catch (Exception e) {
            throw new RetryablePaymentExecutionException(
                    "Exception inspecting broadcast state for transaction " + transactionId + ": " + e.getMessage(), e);
        }
        if (existingTxId.isPresent()) {
            log.info("On-chain transaction {} already broadcast for transaction {}; confirming without re-sending",
                    existingTxId.get(), transactionId);
            confirmTransaction(transactionId, userId);
            return;
        }

        String txid;
        try {
            txid = lightningNodePort.sendOnChain(requestFrom(event), label);
        } catch (Exception e) {
            throw new RetryablePaymentExecutionException(
                    "Exception sending on-chain transaction for transaction " + transactionId + ": " + e.getMessage(), e);
        }
        if (txid == null || txid.isBlank()) {
            throw new RetryablePaymentExecutionException("LND sendCoins returned no transaction id");
        }
        log.info("Broadcast on-chain transaction {} for transaction {}", txid, transactionId);
        confirmTransaction(transactionId, userId);
    }

    /**
     * Conditional UPDATE claiming the PENDING transaction for this delivery. Exactly one concurrent
     * delivery observes 1 affected row; everyone else observes 0 and must not touch LND.
     */
    private boolean claimTransaction(String transactionId) {
        return transactionsRepository.claimPendingForExecution(transactionId) > 0;
    }

    private void releaseExecutionClaim(String transactionId) {
        try {
            transactionsRepository.releaseExecutionClaim(transactionId);
        } catch (Exception e) {
            log.warn("Failed to release execution claim for transaction {}; reconciliation will reclaim it", transactionId, e);
        }
    }

    private void skipUnclaimed(String transactionId, String userId) {
        Optional<TransactionDTOResponse> transaction = loadTransaction(transactionId, userId);
        if (transaction.isEmpty()) {
            throw new RetryablePaymentExecutionException("Transaction not found for execution: " + transactionId);
        }
        String currentStatus = transactionsRepository.findById(transactionId)
                .map(TransactionEntity::getCurrentStatus)
                .orElse(transaction.get().getStatus().name());
        log.info("Transaction {} is not claimable (status {}); another delivery owns it or it is terminal; skipping",
                transactionId, currentStatus);
    }

    private void handleLightningPayment(String transactionId, String userId, String paymentHash, LightningPayment payment) {
        if (payment.status() == LightningPaymentStatus.SUCCEEDED) {
            recordFeeIfPresent(transactionId, payment);
            confirmTransaction(transactionId, userId);
            return;
        }
        if (payment.status() == LightningPaymentStatus.IN_FLIGHT) {
            throw new RetryablePaymentExecutionException("LND payment " + paymentHash + " is IN_FLIGHT");
        }
        if (payment.status() == LightningPaymentStatus.FAILED) {
            String reason = payment.failureReason();
            log.info("LND reported payment failure for transaction {}: {}", transactionId, reason);
            failTransaction(transactionId, reason);
            return;
        }
        throw new RetryablePaymentExecutionException("LND payment outcome is unknown: " + payment.status());
    }

    private Optional<LightningPayment> findPaymentOrThrow(String transactionId, String paymentHash) {
        try {
            return lightningNodePort.findPayment(paymentHash);
        } catch (Exception e) {
            throw new RetryablePaymentExecutionException(
                    "Transport error inspecting payment state for transaction " + transactionId + ": " + e.getMessage(), e);
        }
    }

    private LightningPayment sendPaymentOrThrow(PaymentInitiatedEvent event) {
        PayInvoiceRequestDTO request = normalizedRequest(event);
        try {
            Optional<LightningPayment> result = lightningNodePort.executeLightningPayment(
                    request, defaultFeeLimitSat, defaultTimeoutSeconds);
            if (result.isEmpty()) {
                throw new RetryablePaymentExecutionException(
                        "LND payment returned no terminal result for transaction " + event.getTransactionId());
            }
            return result.get();
        } catch (RetryablePaymentExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new RetryablePaymentExecutionException(
                    "Exception sending payment for transaction " + event.getTransactionId() + ": " + e.getMessage(), e);
        }
    }

    private void recordFeeIfPresent(String transactionId, LightningPayment payment) {
        long feeSat = payment.feeSatRoundedUp();
        if (feeSat > 0) {
            try {
                transactionsPort.addFeeToTransaction(transactionId, feeSat);
            } catch (Exception e) {
                log.warn("Failed to record fee for transaction {}: {}", transactionId, e.getMessage());
            }
        }
    }

    private void confirmTransaction(String transactionId, String userId) {
        transactionsPort.confirmTransaction(transactionId, userId);
    }

    private void failTransaction(String transactionId, String reason) {
        try {
            transactionsPort.failTransaction(transactionId, reason);
        } catch (ApplicationException e) {
            if (e.getMessage() != null && e.getMessage().contains("not valid for failure")) {
                log.info("Transaction {} already terminal, skipping fail", transactionId);
            } else {
                throw e;
            }
        }
    }

    private Optional<TransactionDTOResponse> loadTransaction(String transactionId, String userId) {
        try {
            return transactionsPort.getTransactionById(transactionId, userId);
        } catch (Exception e) {
            log.warn("Failed to load transaction {} for execution facts: {}", transactionId, e.getMessage());
            return Optional.empty();
        }
    }

    private String resolvePaymentHash(PaymentInitiatedEvent event) {
        try {
            DecodedInvoice decoded = invoicesPort.decodeInvoice(event.getPayRequest().getInvoice());
            return decoded.paymentHash();
        } catch (Exception e) {
            log.warn("Failed to decode invoice for transaction {}; proceeding without payment hash",
                    event.getTransactionId(), e);
            return null;
        }
    }

    private PayInvoiceRequestDTO normalizedRequest(PaymentInitiatedEvent event) {
        PayInvoiceRequestDTO request = event.getPayRequest();
        if (request.getInvoice() != null && request.getInvoice().toLowerCase().startsWith("lightning:")) {
            PayInvoiceRequestDTO normalized = new PayInvoiceRequestDTO();
            normalized.setInvoice(request.getInvoice().substring(10));
            normalized.setFeeLimitSat(request.getFeeLimitSat());
            normalized.setTimeoutSeconds(request.getTimeoutSeconds());
            return normalized;
        }
        return request;
    }

    private OnChainPaymentDTOs.SendOnChainRequestDTO requestFrom(OnChainPaymentInitiatedEvent event) {
        return event.getPaymentRequest();
    }

    private String firstPresent(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }
}
