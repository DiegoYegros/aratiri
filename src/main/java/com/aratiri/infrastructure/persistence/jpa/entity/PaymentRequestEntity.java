package com.aratiri.infrastructure.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Table(name = "payment_requests")
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentRequestEntity {

    @Id
    @Setter(AccessLevel.NONE)
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "public_id", nullable = false, length = 32, unique = true)
    private String publicId;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "amount_sats", nullable = false)
    private long amountSats;

    @Column(name = "memo", length = 500)
    private String memo;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "payment_hash", length = 64, unique = true)
    private String paymentHash;

    @Column(name = "preimage", columnDefinition = "TEXT")
    private String preimage;

    @Column(name = "payment_request", columnDefinition = "TEXT")
    private String paymentRequest;

    @Column(name = "invoice_id", length = 36)
    private String invoiceId;

    @Column(name = "idempotency_key", nullable = false, length = 255)
    private String idempotencyKey;

    @Column(name = "idempotency_payload_hash", nullable = false, length = 64)
    private String idempotencyPayloadHash;

    @Setter(AccessLevel.NONE)
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "provision_attempt_count", nullable = false)
    private int provisionAttemptCount;

    @Column(name = "provision_next_attempt_at")
    private Instant provisionNextAttemptAt;

    @Column(name = "provision_locked_until")
    private Instant provisionLockedUntil;

    @Column(name = "provision_locked_by", length = 128)
    private String provisionLockedBy;

    @Column(name = "provision_last_error", columnDefinition = "TEXT")
    private String provisionLastError;

    @Column(name = "cancel_attempt_count", nullable = false)
    private int cancelAttemptCount;

    @Column(name = "cancel_next_attempt_at")
    private Instant cancelNextAttemptAt;

    @Column(name = "cancel_locked_until")
    private Instant cancelLockedUntil;

    @Column(name = "cancel_locked_by", length = 128)
    private String cancelLockedBy;

    @Column(name = "cancel_last_error", columnDefinition = "TEXT")
    private String cancelLastError;
}
