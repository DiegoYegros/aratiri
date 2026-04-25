package com.aratiri.infrastructure.scheduling.job;

import com.aratiri.infrastructure.configuration.NodeOperationProperties;
import com.aratiri.infrastructure.persistence.jpa.entity.NodeOperationEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.NodeOperationStatus;
import com.aratiri.infrastructure.persistence.jpa.entity.NodeOperationType;
import com.aratiri.infrastructure.persistence.jpa.repository.NodeOperationsRepository;
import com.aratiri.payments.application.dto.OnChainPaymentDTOs;
import com.aratiri.payments.application.dto.PayInvoiceRequestDTO;
import com.aratiri.payments.application.port.out.LightningNodePort;
import com.aratiri.payments.infrastructure.json.JsonUtils;
import com.aratiri.transactions.application.port.in.TransactionsPort;
import io.grpc.StatusRuntimeException;
import lnrpc.Payment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class NodeOperationJob {

    private final NodeOperationsRepository nodeOperationsRepository;
    private final NodeOperationProperties nodeOperationProperties;
    private final LightningNodePort lightningNodePort;
    private final TransactionsPort transactionsPort;
    private final NodeOperationState stateManager;
    private final NodeOperationClaimer claimer;

    @Value("${aratiri.payment.default.fee.limit.sat:200}")
    private int defaultFeeLimitSat;

    @Value("${aratiri.payment.default.timeout.seconds:200}")
    private int defaultTimeoutSeconds;

    @Scheduled(fixedDelayString = "${aratiri.node-operations.fixed-delay-ms:1000}")
    public void processOperations() {
        List<NodeOperationEntity> pendingOps = claimer.claimPendingBatch();
        List<NodeOperationEntity> staleOps = claimer.claimStaleBatch();
        List<NodeOperationEntity> broadcastedOps = claimer.claimBroadcastedBatch();

        for (NodeOperationEntity op : pendingOps) {
            try {
                executeOperation(op);
            } catch (Exception e) {
                log.error("Failed to process operation id={}: {}", op.getId(), e.getMessage(), e);
            }
        }

        for (NodeOperationEntity op : staleOps) {
            try {
                executeOperation(op);
            } catch (Exception e) {
                log.error("Failed to process stale operation id={}: {}", op.getId(), e.getMessage(), e);
            }
        }

        for (NodeOperationEntity op : broadcastedOps) {
            try {
                confirmBroadcastedOperation(op);
            } catch (Exception e) {
                log.error("Failed to process broadcasted operation id={}: {}", op.getId(), e.getMessage(), e);
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
            Optional<Payment> existingPayment = lightningNodePort.findPayment(paymentHash);
            if (existingPayment.isPresent()) {
                Payment payment = existingPayment.get();
                PaymentResult result = handleExistingPayment(op, payment);
                if (result.isTerminal()) {
                    return;
                }
            }
        }

        int maxAttempts = nodeOperationProperties.getLightningMaxAttempts();
        if (op.getAttemptCount() >= maxAttempts) {
            stateManager.failTransaction(op.getTransactionId(), "Max attempts reached");
            stateManager.markFailed(op, "Max lightning attempts (" + maxAttempts + ") reached");
            return;
        }

        try {
            executeSendPayment(op);
        } catch (StatusRuntimeException e) {
            handleTransportError(op, e);
        } catch (Exception e) {
            handleException(op, e);
        }
    }

    private PaymentResult handleExistingPayment(NodeOperationEntity op, Payment payment) {
        if (payment.getStatus() == Payment.PaymentStatus.SUCCEEDED) {
            stateManager.recordFeeIfPresent(op.getTransactionId(), payment);
            stateManager.confirmTransaction(op.getTransactionId(), op.getUserId());
            stateManager.markSucceeded(op);
            return PaymentResult.TERMINAL;
        } else if (payment.getStatus() == Payment.PaymentStatus.IN_FLIGHT) {
            log.info("Payment {} is IN_FLIGHT on LND, leaving operation retryable", op.getReferenceId());
            return PaymentResult.TERMINAL;
        } else if (payment.getStatus() == Payment.PaymentStatus.FAILED) {
            stateManager.failTransaction(op.getTransactionId(), payment.getFailureReason().toString());
            stateManager.markFailed(op, "LND reported payment failure: " + payment.getFailureReason());
            return PaymentResult.TERMINAL;
        }
        return PaymentResult.CONTINUE;
    }

    private void executeSendPayment(NodeOperationEntity op) {
        PayInvoiceRequestDTO request = JsonUtils.fromJson(op.getRequestPayload(), PayInvoiceRequestDTO.class);
        Payment finalPayment = lightningNodePort.executeLightningPayment(request, defaultFeeLimitSat, defaultTimeoutSeconds);

        if (finalPayment != null && finalPayment.getStatus() == Payment.PaymentStatus.SUCCEEDED) {
            stateManager.recordFeeIfPresent(op.getTransactionId(), finalPayment);
            stateManager.confirmTransaction(op.getTransactionId(), op.getUserId());
            stateManager.markSucceeded(op);
        } else {
            String reason = finalPayment != null ? finalPayment.getFailureReason().toString() : "Unknown failure";
            stateManager.failTransaction(op.getTransactionId(), reason);
            stateManager.markFailed(op, "LND payment failure: " + reason);
        }
    }

    private void handleTransportError(NodeOperationEntity op, StatusRuntimeException e) {
        int maxAttempts = nodeOperationProperties.getLightningMaxAttempts();
        if (op.getAttemptCount() + 1 >= maxAttempts) {
            stateManager.markFailed(op, "Transport error after max attempts: " + e.getMessage());
        } else {
            log.warn("Transport error for lightning operation id={}, retrying", op.getId());
            op.setLastError(e.getMessage());
            nodeOperationsRepository.save(op);
        }
    }

    private void handleException(NodeOperationEntity op, Exception e) {
        int maxAttempts = nodeOperationProperties.getLightningMaxAttempts();
        if (op.getAttemptCount() + 1 >= maxAttempts) {
            stateManager.markFailed(op, "Exception after max attempts: " + e.getMessage());
        } else {
            log.warn("Exception for lightning operation id={}, retrying", op.getId(), e);
            op.setLastError(e.getMessage());
            nodeOperationsRepository.save(op);
        }
    }

    void executeOnChain(NodeOperationEntity op) {
        int maxAttempts = nodeOperationProperties.getOnchainMaxAttempts();

        if (op.getExternalId() != null) {
            stateManager.confirmTransaction(op.getTransactionId(), op.getUserId());
            stateManager.markSucceeded(op);
            return;
        }

        if (op.getAttemptCount() >= maxAttempts) {
            stateManager.markFailed(op, "Max on-chain attempts (" + maxAttempts + ") reached");
            return;
        }

        String txid;
        try {
            OnChainPaymentDTOs.SendOnChainRequestDTO request = JsonUtils.fromJson(op.getRequestPayload(), OnChainPaymentDTOs.SendOnChainRequestDTO.class);
            txid = lightningNodePort.sendOnChain(request);
        } catch (Exception e) {
            log.error("On-chain send failed for operation id={}: {}", op.getId(), e.getMessage());
            op.setStatus(NodeOperationStatus.UNKNOWN_OUTCOME);
            op.setLastError(e.getMessage());
            nodeOperationsRepository.save(op);
            return;
        }

        op.setExternalId(txid);
        op.setStatus(NodeOperationStatus.BROADCASTED);
        nodeOperationsRepository.save(op);

        stateManager.confirmTransaction(op.getTransactionId(), op.getUserId());
        stateManager.markSucceeded(op);
    }

    private enum PaymentResult {
        TERMINAL(true),
        CONTINUE(false);

        private final boolean terminal;

        PaymentResult(boolean terminal) {
            this.terminal = terminal;
        }

        boolean isTerminal() {
            return terminal;
        }
    }
}
