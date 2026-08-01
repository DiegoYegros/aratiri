package com.aratiri.invoices.domain;

/**
 * Full invoice creation result for internal callers that need hash and local id,
 * not only the BOLT11 string exposed on the HTTP invoice API.
 */
public record CreatedLightningInvoice(
        String id,
        String paymentHash,
        String paymentRequest,
        long expirySeconds
) {
}
