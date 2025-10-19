package com.aratiri.job;

import com.aratiri.entity.TransactionEntity;
import com.aratiri.payments.application.port.in.PaymentPort;
import com.aratiri.repository.TransactionsRepository;
import com.aratiri.service.TransactionsService;
import lnrpc.Payment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
public class TransactionReconciliationJob {

    private static final Logger logger = LoggerFactory.getLogger(TransactionReconciliationJob.class);
    private final TransactionsRepository transactionsRepository;
    private final PaymentPort paymentPort;
    private final TransactionsService transactionsService;

    public TransactionReconciliationJob(
            TransactionsRepository transactionsRepository,
            PaymentPort paymentPort,
            TransactionsService transactionsService) {
        this.transactionsRepository = transactionsRepository;
        this.paymentPort = paymentPort;
        this.transactionsService = transactionsService;
    }

    @Scheduled(fixedDelay = 60000)
    public void reconcilePendingPayments() {
        logger.debug("Starting pending payments reconciliation task.");
        Instant fiveMinutesAgo = Instant.now().minus(5, ChronoUnit.MINUTES);
        List<TransactionEntity> pendingTransactions = transactionsRepository.findPendingTransactionsOlderThan(fiveMinutesAgo);
        if (pendingTransactions.isEmpty()) {
            logger.debug("No pending transactions to reconcile.");
            return;
        }
        logger.info("Found {} pending transactions to reconcile.", pendingTransactions.size());
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
        logger.info("Reconciling transaction ID: {}, Payment Hash: {}", transaction.getId(), paymentHash);
        Optional<Payment> paymentStatusOpt = paymentPort.checkPaymentStatusOnNode(paymentHash);
        if (paymentStatusOpt.isEmpty()) {
            logger.warn("Payment with hash {} not found on LND node.", paymentHash);
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