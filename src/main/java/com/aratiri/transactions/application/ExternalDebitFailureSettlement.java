package com.aratiri.transactions.application;

public record ExternalDebitFailureSettlement(
        String transactionId,
        String failureReason
) {
}
