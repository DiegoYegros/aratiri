package com.aratiri.invoices.domain;

import java.time.LocalDateTime;

public record LightningInvoice(
        String id,
        String userId,
        String paymentHash,
        String preimage,
        String paymentRequest,
        InvoiceState invoiceState,
        long amountSats,
        LocalDateTime createdAt,
        long expiry,
        long amountPaidSats,
        LocalDateTime settledAt,
        String memo
) {

    public LightningInvoice withId(String newId) {
        return new LightningInvoice(
                newId,
                userId,
                paymentHash,
                preimage,
                paymentRequest,
                invoiceState,
                amountSats,
                createdAt,
                expiry,
                amountPaidSats,
                settledAt,
                memo
        );
    }

    public LightningInvoice withState(InvoiceState newState) {
        return new LightningInvoice(
                id,
                userId,
                paymentHash,
                preimage,
                paymentRequest,
                newState,
                amountSats,
                createdAt,
                expiry,
                amountPaidSats,
                settledAt,
                memo
        );
    }

    public enum InvoiceState {
        OPEN,
        ACCEPTED,
        SETTLED,
        CANCELED
    }
}
