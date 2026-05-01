package com.aratiri.invoices.application;

import com.aratiri.invoices.domain.LightningInvoice;

import java.util.Objects;

public record InvoiceSettlementFacts(
        String paymentHash,
        String memo,
        String description,
        String externalReference,
        String metadata
) {

    public InvoiceSettlementFacts {
        Objects.requireNonNull(paymentHash, "paymentHash must not be null");
        Objects.requireNonNull(description, "description must not be null");
    }

    public static InvoiceSettlementFacts from(LightningInvoice invoice) {
        return new InvoiceSettlementFacts(
                invoice.paymentHash(),
                invoice.memo(),
                description(invoice.paymentHash(), invoice.memo()),
                invoice.externalReference(),
                invoice.metadata()
        );
    }

    public static InvoiceSettlementFacts missing(String paymentHash) {
        return new InvoiceSettlementFacts(
                paymentHash,
                null,
                fallbackDescription(paymentHash),
                null,
                null
        );
    }

    private static String description(String paymentHash, String memo) {
        return memo != null ? memo : fallbackDescription(paymentHash);
    }

    private static String fallbackDescription(String paymentHash) {
        int prefixLength = Math.min(paymentHash.length(), 10);
        return String.format("Payment received for invoice (hash: %s...)", paymentHash.substring(0, prefixLength));
    }
}
