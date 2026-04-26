package com.aratiri.transactions.application;

import com.aratiri.transactions.application.dto.TransactionStatus;

public record TransactionSettlementResult(
        String transactionId,
        TransactionStatus status,
        long amountSat,
        Long balanceAfterSat
) {
    static TransactionSettlementResult from(TransactionState state) {
        return new TransactionSettlementResult(
                state.transaction().getId(),
                state.status(),
                state.amountSat(),
                state.balanceAfterSat()
        );
    }
}
