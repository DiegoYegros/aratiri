package com.aratiri.requests.application.event;

/**
 * Kafka record for {@code payment-request.provision.requested}. Keyed by request id so a single
 * partition preserves per-request ordering.
 */
public record PaymentRequestProvisionRequestedEvent(String requestId) {
}
