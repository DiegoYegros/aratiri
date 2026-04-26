package com.aratiri.webhooks.application;

import com.aratiri.transactions.application.dto.TransactionStatus;
import com.aratiri.transactions.application.dto.TransactionType;

import java.util.Objects;
import java.util.Set;

public record PaymentWebhookFacts(
        String transactionId,
        String userId,
        TransactionType type,
        long amountSat,
        TransactionStatus status,
        String referenceId,
        String externalReference,
        String metadata,
        Long balanceAfterSat,
        String failureReason
) {
    private static final Set<TransactionType> DEBIT_TYPES = Set.of(
            TransactionType.LIGHTNING_DEBIT,
            TransactionType.ONCHAIN_DEBIT,
            TransactionType.INVOICE_DEBIT
    );

    public PaymentWebhookFacts {
        Objects.requireNonNull(transactionId, "transactionId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }

    public static PaymentWebhookFacts accepted(
            String transactionId,
            String userId,
            TransactionType type,
            long amountSat,
            String referenceId,
            String externalReference,
            String metadata
    ) {
        return new PaymentWebhookFacts(
                transactionId,
                userId,
                type,
                amountSat,
                TransactionStatus.PENDING,
                referenceId,
                externalReference,
                metadata,
                null,
                null
        );
    }

    public boolean isDebitPayment() {
        return DEBIT_TYPES.contains(type);
    }
}
