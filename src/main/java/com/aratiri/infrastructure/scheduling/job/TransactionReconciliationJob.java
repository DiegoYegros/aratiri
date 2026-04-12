package com.aratiri.infrastructure.scheduling.job;

import com.aratiri.admin.application.port.out.NodeSettingsPort;
import com.aratiri.admin.domain.NodeSettings;
import com.aratiri.infrastructure.persistence.jpa.entity.TransactionEntity;
import com.aratiri.infrastructure.persistence.jpa.repository.TransactionsRepository;
import com.aratiri.payments.application.port.in.PaymentsPort;
import com.aratiri.transactions.application.port.in.TransactionsPort;
import lnrpc.Payment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class TransactionReconciliationJob {

    private static final Logger logger = LoggerFactory.getLogger(TransactionReconciliationJob.class);
    private final TransactionsRepository transactionsRepository;
    private final NodeSettingsPort nodeSettingsPort;
    private final PaymentsPort paymentsPort;
    private final TransactionsPort transactionsService;

    public TransactionReconciliationJob(
            TransactionsRepository transactionsRepository,
            NodeSettingsPort nodeSettingsPort,
            PaymentsPort paymentsPort,
            TransactionsPort transactionsService) {
        this.transactionsRepository = transactionsRepository;
        this.nodeSettingsPort = nodeSettingsPort;
        this.paymentsPort = paymentsPort;
        this.transactionsService = transactionsService;
    }

    @Scheduled(fixedDelay = 60000)
    public void reconcilePendingPayments() {
        logger.debug("Starting pending payments reconciliation task.");
        NodeSettings settings = nodeSettingsPort.loadSettings();
        long minAgeMs = Math.max(0L, settings.transactionReconciliationMinAgeMs());
        Instant reconciliationThreshold = Instant.now().minusMillis(minAgeMs);
        List<TransactionEntity> pendingTransactions = transactionsRepository.findPendingTransactionsOlderThan(reconciliationThreshold);
        if (pendingTransactions.isEmpty()) {
            logger.debug("No pending transactions to reconcile.");
            return;
        }
        logger.info(
                "Found {} pending transactions to reconcile using a minimum age of {} ms.",
                pendingTransactions.size(),
                minAgeMs
        );
        for (TransactionEntity transaction : pendingTransactions) {
            try {
                reconcileTransaction(transaction);
            } catch (Exception e) {
                logger.error("Error reconciling transaction ID: {}", transaction.getId(), e);
            }
        }
        logger.info("Finished pending payments reconciliation task.");
    }

    private void reconcileTransaction(TransactionEntity transaction) {
        String paymentHash = transaction.getReferenceId();
        if (paymentHash == null || paymentHash.isBlank()) {
            logger.debug("Skipping reconciliation for transaction {} because it has no payment hash.", transaction.getId());
            return;
        }
        logger.info("Reconciling transaction ID: {}, Payment Hash: {}", transaction.getId(), paymentHash);
        Optional<Payment> paymentStatusOpt = paymentsPort.checkPaymentStatusOnNode(paymentHash);
        if (paymentStatusOpt.isEmpty()) {
            String failureReason = String.format("Payment status for paymentHash: %s not found", paymentHash);
            logger.warn("Payment with hash {} not found on LND node.", paymentHash);
            transactionsService.failTransaction(transaction.getId(), failureReason);
            return;
        }
        Payment payment = paymentStatusOpt.get();
        switch (payment.getStatus()) {
            case SUCCEEDED:
                logger.info("Transaction {} SUCCEEDED on LND. Confirming in db.", transaction.getId());
                transactionsService.confirmTransaction(transaction.getId(), transaction.getUserId());
                break;
            case FAILED:
                String failureReason = payment.getFailureReason().toString();
                logger.warn("Transaction {} FAILED on LND. Reason: {}. Failing in db.", transaction.getId(), failureReason);
                transactionsService.failTransaction(transaction.getId(), failureReason);
                break;
            case IN_FLIGHT:
                logger.info("Transaction {} is still IN_FLIGHT on LND. Will check again later.", transaction.getId());
                break;
            default:
                logger.warn("Unknown status for transaction {}: {}", transaction.getId(), payment.getStatus());
                break;
        }
    }
}
