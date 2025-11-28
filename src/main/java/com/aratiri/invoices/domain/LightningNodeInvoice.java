package com.aratiri.invoices.domain;

public record LightningNodeInvoice(
        String paymentRequest,
        LightningInvoice.InvoiceState state,
        long amountPaidSats,
        long valueSats
) {
}
