package com.aratiri.requests.application.event;

/**
 * Kafka record for {@code payment-request.cancel.requested}. Keyed by request id so a single
 * partition preserves per-request ordering.
 */
public record PaymentRequestCancelRequestedEvent(String requestId) {
}
