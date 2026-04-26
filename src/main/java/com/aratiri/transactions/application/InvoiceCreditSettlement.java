package com.aratiri.transactions.application;

public record InvoiceCreditSettlement(
        String userId,
        long amountSat,
        String paymentHash,
        String description,
        String externalReference,
        String metadata
) {
}
