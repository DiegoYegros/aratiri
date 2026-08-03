package com.aratiri.requests.application.port.out;

import com.aratiri.requests.domain.PaymentRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PaymentRequestPersistencePort {

    /**
     * Transaction-scoped PostgreSQL advisory lock for (owner, Idempotency-Key).
     * Must be held before inserting durable intent so concurrent first creates serialize.
     */
    void lockCreateSlot(String userId, String idempotencyKey);

    PaymentRequest save(PaymentRequest request);

    Optional<PaymentRequest> findById(String id);

    /**
     * Pessimistic row lock for provision-finalize fencing inside an outer transaction.
     */
    Optional<PaymentRequest> findByIdForUpdate(String id);

    Optional<PaymentRequest> findByPublicId(String publicId);

    Optional<PaymentRequest> findByPublicIdAndUserId(String publicId, String userId);

    Optional<PaymentRequest> findByUserIdAndIdempotencyKey(String userId, String idempotencyKey);

    Optional<PaymentRequest> findByPaymentHash(String paymentHash);

    List<PaymentRequest> findByUserIdWithCursor(String userId, Instant cursorCreatedAt, String cursorId, int limit);

    List<PaymentRequest> findByUserIdFirstPage(String userId, int limit);

    /**
     * Short transactional update: any non-PAID status → PAID by payment hash (PAID-wins).
     */
    int markPaidByPaymentHash(String paymentHash, Instant paidAt);

    /**
     * Durably transitions OPEN or PROVISIONING → CANCEL_PENDING for the owner.
     */
    int markCancelPendingIfPayable(String publicId, String userId, Instant now);

    List<PaymentRequest> findDueProvisioning(Instant now, int limit);

    List<PaymentRequest> findDueCancellations(Instant now, int limit);

    int claimProvisioning(String id, String lockedBy, Instant lockedUntil, Instant now);

    int claimCancellation(String id, String lockedBy, Instant lockedUntil, Instant now);

    /**
     * Fenced OPEN finalize: only the active provision claim token may clear the lease.
     */
    int finalizeProvisioningOpen(String id, String paymentRequest, String invoiceId, String lockedBy);

    /**
     * Fenced CANCELLED finalize: only the active cancel claim token may clear the lease.
     */
    int finalizeCancelled(String id, Instant cancelledAt, String lockedBy);

    int markProvisioningFailed(String id, String error, String lockedBy);

    /**
     * Conditional FAILED → PROVISIONING requeue. Returns updated row count (0 if not FAILED).
     */
    int requeueFailedProvisioning(String publicId, Instant now);

    int scheduleProvisioningRetry(String id, String error, Instant nextAttemptAt, String lockedBy);

    int scheduleCancelRetry(String id, String error, Instant nextAttemptAt, String lockedBy);

    long countDueProvisioning(Instant now);

    long countInProgressProvisioning(Instant now);

    long countFailedProvisioning();

    long countDueCancellations(Instant now);

    long countInProgressCancellations(Instant now);

    long countExhaustedCancellations(int maxAttempts);

    List<PaymentRequest> findFailed(int limit);

    List<PaymentRequest> findExhaustedCancellations(int maxAttempts, int limit);
}
