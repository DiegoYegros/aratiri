package com.aratiri.requests.domain;

import java.time.Instant;

/**
 * Stored statuses are OPEN, PAID, or CANCELLED. EXPIRED is derived when the
 * stored status is still OPEN and {@code clockInstant >= expiresAt}.
 * Settlement to PAID is authoritative over CANCELLED and derived EXPIRED.
 */
public record PaymentRequest(
        String id,
        String publicId,
        String userId,
        long amountSats,
        String memo,
        PaymentRequestStatus storedStatus,
        String paymentHash,
        String paymentRequest,
        String invoiceId,
        String idempotencyKey,
        String idempotencyPayloadHash,
        Instant createdAt,
        Instant expiresAt,
        Instant paidAt,
        Instant cancelledAt
) {

    public PaymentRequestStatus effectiveStatus(Instant now) {
        if (storedStatus == PaymentRequestStatus.PAID) {
            return PaymentRequestStatus.PAID;
        }
        if (storedStatus == PaymentRequestStatus.CANCELLED) {
            return PaymentRequestStatus.CANCELLED;
        }
        if (storedStatus == PaymentRequestStatus.OPEN && !now.isBefore(expiresAt)) {
            return PaymentRequestStatus.EXPIRED;
        }
        return PaymentRequestStatus.OPEN;
    }

    public boolean isPayable(Instant now) {
        return effectiveStatus(now) == PaymentRequestStatus.OPEN;
    }

    public PaymentRequest withPaid(Instant paidAt) {
        return new PaymentRequest(
                id,
                publicId,
                userId,
                amountSats,
                memo,
                PaymentRequestStatus.PAID,
                paymentHash,
                paymentRequest,
                invoiceId,
                idempotencyKey,
                idempotencyPayloadHash,
                createdAt,
                expiresAt,
                paidAt,
                cancelledAt
        );
    }

    public PaymentRequest withCancelled(Instant cancelledAt) {
        return new PaymentRequest(
                id,
                publicId,
                userId,
                amountSats,
                memo,
                PaymentRequestStatus.CANCELLED,
                paymentHash,
                paymentRequest,
                invoiceId,
                idempotencyKey,
                idempotencyPayloadHash,
                createdAt,
                expiresAt,
                paidAt,
                cancelledAt
        );
    }
}
