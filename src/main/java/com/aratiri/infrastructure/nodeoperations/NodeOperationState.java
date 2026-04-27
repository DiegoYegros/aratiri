package com.aratiri.infrastructure.nodeoperations;

import com.aratiri.infrastructure.configuration.NodeOperationProperties;
import com.aratiri.infrastructure.persistence.jpa.entity.NodeOperationEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.NodeOperationStatus;
import com.aratiri.infrastructure.persistence.jpa.repository.NodeOperationsRepository;
import com.aratiri.shared.exception.AratiriException;
import com.aratiri.transactions.application.port.in.TransactionsPort;
import lnrpc.Payment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
class NodeOperationState {

    private final NodeOperationsRepository nodeOperationsRepository;
    private final TransactionsPort transactionsPort;
    private final NodeOperationProperties nodeOperationProperties;

    void recordFeeIfPresent(String transactionId, Payment payment) {
        long feeSat = payment.getFeeSat();
        long feeMsat = payment.getFeeMsat();
        if (feeSat <= 0 && feeMsat > 0) {
            feeSat = (feeMsat + 999) / 1000;
        }
        if (feeSat > 0) {
            try {
                transactionsPort.addFeeToTransaction(transactionId, feeSat);
            } catch (Exception e) {
                log.warn("Failed to record fee for transaction {}: {}", transactionId, e.getMessage());
            }
        }
    }

    void confirmTransaction(String transactionId, String userId) {
        transactionsPort.confirmTransaction(transactionId, userId);
    }

    void failTransaction(String transactionId, String reason) {
        try {
            transactionsPort.failTransaction(transactionId, reason);
        } catch (AratiriException e) {
            if (e.getMessage() != null && e.getMessage().contains("not valid for failure")) {
                log.info("Transaction {} already failed, skipping fail", transactionId);
            } else {
                throw e;
            }
        }
    }

    @Transactional
    void markRetryable(NodeOperationEntity op, String error) {
        op.setStatus(NodeOperationStatus.PENDING);
        op.setLastError(error);
        op.setNextAttemptAt(Instant.now().plusMillis(nodeOperationProperties.getFixedDelayMs()));
        op.setLockedBy(null);
        op.setLockedUntil(null);
        nodeOperationsRepository.save(op);
    }

    @Transactional
    void markSucceeded(NodeOperationEntity op) {
        op.setStatus(NodeOperationStatus.SUCCEEDED);
        op.setCompletedAt(Instant.now());
        op.setLockedBy(null);
        op.setLockedUntil(null);
        nodeOperationsRepository.save(op);
    }

    @Transactional
    void markFailed(NodeOperationEntity op, String error) {
        op.setStatus(NodeOperationStatus.FAILED);
        op.setLastError(error);
        op.setCompletedAt(Instant.now());
        op.setLockedBy(null);
        op.setLockedUntil(null);
        nodeOperationsRepository.save(op);
    }
}
