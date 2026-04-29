package com.aratiri.webhooks.application;

import java.util.Objects;

public record AccountBalanceChangedWebhookFacts(
        String transactionId,
        String userId,
        String externalReference,
        String metadata,
        long amountSat,
        String referenceId,
        String ledgerEntryId,
        Long balanceAfterSat
) {
    public AccountBalanceChangedWebhookFacts {
        Objects.requireNonNull(transactionId, "transactionId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(ledgerEntryId, "ledgerEntryId must not be null");
    }
}
