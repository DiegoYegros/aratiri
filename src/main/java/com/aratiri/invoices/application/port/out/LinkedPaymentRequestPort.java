package com.aratiri.invoices.application.port.out;

import java.time.Instant;
import java.util.Optional;

/**
 * Notifies linked single-use payment requests when a Lightning invoice settles.
 * Settlement is authoritative: a real credit must never be suppressed by cancel/expiry races.
 */
public interface LinkedPaymentRequestPort {

    void markPaidByPaymentHash(String paymentHash, Instant paidAt);

    /**
     * Returns durable provisioning data for an owned payment request even when the local
     * lightning_invoices row was not yet finalized.
     */
    Optional<OwnedPaymentRequestInvoiceSeed> findOwnedInvoiceSeedByPaymentHash(String paymentHash);
}
