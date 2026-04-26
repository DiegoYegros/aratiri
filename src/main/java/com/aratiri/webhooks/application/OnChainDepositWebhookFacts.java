package com.aratiri.webhooks.application;

import com.aratiri.transactions.application.dto.TransactionStatus;

import java.util.Objects;

public record OnChainDepositWebhookFacts(
        String transactionId,
        String userId,
        long amountSat,
        TransactionStatus status,
        String referenceId,
        String externalReference,
        String metadata,
        Long balanceAfterSat
) {
    public OnChainDepositWebhookFacts {
        Objects.requireNonNull(transactionId, "transactionId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(referenceId, "referenceId must not be null");
    }
}
