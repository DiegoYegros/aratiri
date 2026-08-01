package com.aratiri.invoices.application.port.out;

import java.time.Instant;

/**
 * Notifies linked single-use payment requests when a Lightning invoice settles.
 * Settlement is authoritative: a real credit must never be suppressed by cancel/expiry races.
 */
public interface LinkedPaymentRequestPort {

    void markPaidByPaymentHash(String paymentHash, Instant paidAt);
}
