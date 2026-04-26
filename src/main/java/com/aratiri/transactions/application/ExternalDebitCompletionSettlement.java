package com.aratiri.transactions.application;

public record ExternalDebitCompletionSettlement(
        String transactionId,
        String userId
) {
}
