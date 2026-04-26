package com.aratiri.transactions.application;

import com.aratiri.infrastructure.persistence.jpa.entity.TransactionEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.TransactionEventEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.TransactionEventType;
import com.aratiri.transactions.application.dto.TransactionStatus;

import java.util.List;

record TransactionState(
        TransactionEntity transaction,
        List<TransactionEventEntity> events,
        TransactionStatus status,
        long amountSat,
        Long balanceAfterSat,
        String failureReason
) {

    static TransactionState from(TransactionEntity transaction, List<TransactionEventEntity> events) {
        TransactionStatus status = TransactionStatus.PENDING;
        long amountSat = transaction.getAmount();
        Long balanceAfterSat = null;
        String failureReason = null;
        for (TransactionEventEntity event : events) {
            if (event.getEventType() == TransactionEventType.STATUS_CHANGED && event.getStatus() != null) {
                status = event.getStatus();
                if (event.getBalanceAfter() != null) {
                    balanceAfterSat = event.getBalanceAfter();
                }
                if (event.getDetails() != null) {
                    failureReason = event.getDetails();
                }
            } else if (event.getEventType() == TransactionEventType.FEE_ADDED && event.getAmountDelta() != null) {
                amountSat += event.getAmountDelta();
            }
        }
        return new TransactionState(transaction, events, status, amountSat, balanceAfterSat, failureReason);
    }

    boolean isPending() {
        return status == TransactionStatus.PENDING;
    }
}
