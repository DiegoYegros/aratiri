package com.aratiri.payments.domain;

public record InternalLightningInvoice(
        String userId,
        InvoiceState state
) {
    public enum InvoiceState {
        PENDING,
        SETTLED
    }
}
