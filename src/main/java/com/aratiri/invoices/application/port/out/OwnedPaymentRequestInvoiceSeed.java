package com.aratiri.invoices.application.port.out;

/**
 * Minimal seed to materialize a local lightning_invoices row for an owned payment request
 * that committed durable preimage/hash intent before local invoice finalization.
 */
public record OwnedPaymentRequestInvoiceSeed(
        String userId,
        String paymentHash,
        String preimage,
        String paymentRequest,
        long amountSats,
        String memo,
        long expirySeconds
) {
}
