package com.aratiri.webhooks.application;

import com.aratiri.invoices.domain.LightningInvoice;

import java.util.Objects;

public record InvoiceCreatedWebhookFacts(
        String invoiceId,
        String userId,
        String paymentHash,
        String paymentRequest,
        long amountSat,
        String memo,
        String externalReference,
        String metadata
) {
    public InvoiceCreatedWebhookFacts {
        Objects.requireNonNull(invoiceId, "invoiceId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(paymentHash, "paymentHash must not be null");
    }

    public static InvoiceCreatedWebhookFacts from(LightningInvoice invoice) {
        return new InvoiceCreatedWebhookFacts(
                invoice.id(),
                invoice.userId(),
                invoice.paymentHash(),
                invoice.paymentRequest(),
                invoice.amountSats(),
                invoice.memo(),
                invoice.externalReference(),
                invoice.metadata()
        );
    }
}
