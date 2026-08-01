package com.aratiri.requests.application.port.out;

import com.aratiri.requests.domain.PaymentRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PaymentRequestPersistencePort {

    /**
     * Transaction-scoped PostgreSQL advisory lock for (owner, Idempotency-Key).
     * Must be held before Lightning invoice minting so concurrent first creates serialize.
     */
    void lockCreateSlot(String userId, String idempotencyKey);

    PaymentRequest save(PaymentRequest request);

    Optional<PaymentRequest> findByPublicId(String publicId);

    Optional<PaymentRequest> findByPublicIdAndUserId(String publicId, String userId);

    Optional<PaymentRequest> findByUserIdAndIdempotencyKey(String userId, String idempotencyKey);

    List<PaymentRequest> findByUserIdWithCursor(String userId, Instant cursorCreatedAt, String cursorId, int limit);

    List<PaymentRequest> findByUserIdFirstPage(String userId, int limit);

    /**
     * Short transactional update: OPEN/CANCELLED → PAID by payment hash (PAID-wins).
     */
    int markPaidByPaymentHash(String paymentHash, Instant paidAt);

    /**
     * Short transactional update: OPEN → CANCELLED when still payable for the owner.
     */
    int cancelIfOpen(String publicId, String userId, Instant cancelledAt, Instant now);
}
