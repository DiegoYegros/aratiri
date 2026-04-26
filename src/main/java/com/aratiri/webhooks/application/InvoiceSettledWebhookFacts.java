package com.aratiri.webhooks.application;

import com.aratiri.transactions.application.dto.TransactionStatus;

import java.util.Objects;

public record InvoiceSettledWebhookFacts(
        String transactionId,
        String userId,
        String paymentHash,
        long amountSat,
        TransactionStatus status,
        String referenceId,
        String externalReference,
        String metadata,
        Long balanceAfterSat
) {
    public InvoiceSettledWebhookFacts {
        Objects.requireNonNull(transactionId, "transactionId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(paymentHash, "paymentHash must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }
}
