package com.aratiri.requests.domain;

import java.time.Instant;

/**
 * Stored statuses: PROVISIONING, OPEN, CANCEL_PENDING, CANCELLED, PAID, FAILED.
 * EXPIRED is derived when stored status is OPEN and {@code clockInstant >= expiresAt}.
 * Settlement to PAID is authoritative over every other stored status.
 */
public record PaymentRequest(
        String id,
        String publicId,
        String userId,
        long amountSats,
        String memo,
        PaymentRequestStatus storedStatus,
        String paymentHash,
        String preimage,
        String paymentRequest,
        String invoiceId,
        String idempotencyKey,
        String idempotencyPayloadHash,
        Instant createdAt,
        Instant expiresAt,
        Instant paidAt,
        Instant cancelledAt,
        int provisionAttemptCount,
        Instant provisionNextAttemptAt,
        Instant provisionLockedUntil,
        String provisionLockedBy,
        String provisionLastError,
        int cancelAttemptCount,
        Instant cancelNextAttemptAt,
        Instant cancelLockedUntil,
        String cancelLockedBy,
        String cancelLastError
) {

    public PaymentRequestStatus effectiveStatus(Instant now) {
        if (storedStatus == PaymentRequestStatus.PAID) {
            return PaymentRequestStatus.PAID;
        }
        if (storedStatus == PaymentRequestStatus.FAILED) {
            return PaymentRequestStatus.FAILED;
        }
        if (storedStatus == PaymentRequestStatus.CANCELLED) {
            return PaymentRequestStatus.CANCELLED;
        }
        if (storedStatus == PaymentRequestStatus.CANCEL_PENDING) {
            return PaymentRequestStatus.CANCEL_PENDING;
        }
        if (storedStatus == PaymentRequestStatus.PROVISIONING) {
            return PaymentRequestStatus.PROVISIONING;
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
        return copyWith(
                PaymentRequestStatus.PAID,
                paymentRequest,
                invoiceId,
                paidAt,
                cancelledAt,
                provisionAttemptCount,
                provisionNextAttemptAt,
                provisionLockedUntil,
                provisionLockedBy,
                provisionLastError,
                cancelAttemptCount,
                cancelNextAttemptAt,
                cancelLockedUntil,
                cancelLockedBy,
                cancelLastError
        );
    }

    public PaymentRequest withCancelled(Instant cancelledAt) {
        return copyWith(
                PaymentRequestStatus.CANCELLED,
                paymentRequest,
                invoiceId,
                paidAt,
                cancelledAt,
                provisionAttemptCount,
                provisionNextAttemptAt,
                provisionLockedUntil,
                provisionLockedBy,
                provisionLastError,
                cancelAttemptCount,
                cancelNextAttemptAt,
                cancelLockedUntil,
                cancelLockedBy,
                cancelLastError
        );
    }

    public PaymentRequest withOpen(String bolt11, String invoiceId) {
        return copyWith(
                PaymentRequestStatus.OPEN,
                bolt11,
                invoiceId,
                paidAt,
                cancelledAt,
                provisionAttemptCount,
                null,
                null,
                null,
                null,
                cancelAttemptCount,
                cancelNextAttemptAt,
                cancelLockedUntil,
                cancelLockedBy,
                cancelLastError
        );
    }

    public PaymentRequest withCancelPending(Instant now) {
        return copyWith(
                PaymentRequestStatus.CANCEL_PENDING,
                paymentRequest,
                invoiceId,
                paidAt,
                null,
                provisionAttemptCount,
                provisionNextAttemptAt,
                null,
                null,
                provisionLastError,
                cancelAttemptCount,
                now,
                null,
                null,
                null
        );
    }

    public PaymentRequest withFailed(String lastError) {
        return copyWith(
                PaymentRequestStatus.FAILED,
                paymentRequest,
                invoiceId,
                paidAt,
                cancelledAt,
                provisionAttemptCount,
                null,
                null,
                null,
                lastError,
                cancelAttemptCount,
                cancelNextAttemptAt,
                cancelLockedUntil,
                cancelLockedBy,
                cancelLastError
        );
    }

    private PaymentRequest copyWith(
            PaymentRequestStatus status,
            String bolt11,
            String invoiceId,
            Instant paidAt,
            Instant cancelledAt,
            int provisionAttemptCount,
            Instant provisionNextAttemptAt,
            Instant provisionLockedUntil,
            String provisionLockedBy,
            String provisionLastError,
            int cancelAttemptCount,
            Instant cancelNextAttemptAt,
            Instant cancelLockedUntil,
            String cancelLockedBy,
            String cancelLastError
    ) {
        return new PaymentRequest(
                id,
                publicId,
                userId,
                amountSats,
                memo,
                status,
                paymentHash,
                preimage,
                bolt11,
                invoiceId,
                idempotencyKey,
                idempotencyPayloadHash,
                createdAt,
                expiresAt,
                paidAt,
                cancelledAt,
                provisionAttemptCount,
                provisionNextAttemptAt,
                provisionLockedUntil,
                provisionLockedBy,
                provisionLastError,
                cancelAttemptCount,
                cancelNextAttemptAt,
                cancelLockedUntil,
                cancelLockedBy,
                cancelLastError
        );
    }
}
