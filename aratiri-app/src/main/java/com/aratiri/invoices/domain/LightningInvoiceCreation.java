package com.aratiri.invoices.domain;

public record LightningInvoiceCreation(
        String paymentRequest,
        String paymentHash,
        long expiry
) {
}
