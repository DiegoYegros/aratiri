package com.aratiri.infrastructure.persistence.jpa.repository;

import com.aratiri.infrastructure.persistence.jpa.entity.PaymentRequestEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PaymentRequestRepository extends JpaRepository<PaymentRequestEntity, String> {

    Optional<PaymentRequestEntity> findByPublicId(String publicId);

    Optional<PaymentRequestEntity> findByPublicIdAndUserId(String publicId, String userId);

    Optional<PaymentRequestEntity> findByUserIdAndIdempotencyKey(String userId, String idempotencyKey);

    Optional<PaymentRequestEntity> findByPaymentHash(String paymentHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM PaymentRequestEntity r WHERE r.id = :id")
    Optional<PaymentRequestEntity> findByIdForUpdate(@Param("id") String id);

    @Query("SELECT r FROM PaymentRequestEntity r WHERE r.userId = :userId "
            + "AND (r.createdAt < :cursorCreatedAt OR (r.createdAt = :cursorCreatedAt AND r.id < :cursorId)) "
            + "ORDER BY r.createdAt DESC, r.id DESC")
    List<PaymentRequestEntity> findByUserIdWithCursor(
            @Param("userId") String userId,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") String cursorId,
            Pageable pageable
    );

    @Query("SELECT r FROM PaymentRequestEntity r WHERE r.userId = :userId "
            + "ORDER BY r.createdAt DESC, r.id DESC")
    List<PaymentRequestEntity> findByUserIdOrderByCreatedAtDescIdDesc(
            @Param("userId") String userId,
            Pageable pageable
    );

    /**
     * Marks a linked request PAID. Wins over every non-PAID stored status so a real
     * invoice settlement is never suppressed by provision/cancel/expiry races.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PaymentRequestEntity r SET r.status = 'PAID', r.paidAt = :paidAt, "
            + "r.provisionLockedUntil = NULL, r.provisionLockedBy = NULL, "
            + "r.cancelLockedUntil = NULL, r.cancelLockedBy = NULL "
            + "WHERE r.paymentHash = :paymentHash AND r.status <> 'PAID'")
    int markPaidByPaymentHash(@Param("paymentHash") String paymentHash, @Param("paidAt") Instant paidAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PaymentRequestEntity r SET r.status = 'CANCEL_PENDING', "
            + "r.cancelNextAttemptAt = :now, r.cancelLastError = NULL, "
            + "r.provisionLockedUntil = NULL, r.provisionLockedBy = NULL "
            + "WHERE r.publicId = :publicId AND r.userId = :userId "
            + "AND r.status IN ('OPEN', 'PROVISIONING')")
    int markCancelPendingIfPayable(
            @Param("publicId") String publicId,
            @Param("userId") String userId,
            @Param("now") Instant now
    );

    @Query("SELECT r FROM PaymentRequestEntity r WHERE r.status = 'PROVISIONING' "
            + "AND r.provisionNextAttemptAt <= :now "
            + "AND (r.provisionLockedUntil IS NULL OR r.provisionLockedUntil < :now) "
            + "ORDER BY r.provisionNextAttemptAt ASC, r.id ASC")
    List<PaymentRequestEntity> findDueProvisioning(Instant now, Pageable pageable);

    @Query("SELECT r FROM PaymentRequestEntity r WHERE r.status = 'CANCEL_PENDING' "
            + "AND r.cancelNextAttemptAt <= :now "
            + "AND (r.cancelLockedUntil IS NULL OR r.cancelLockedUntil < :now) "
            + "ORDER BY r.cancelNextAttemptAt ASC, r.id ASC")
    List<PaymentRequestEntity> findDueCancellations(Instant now, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PaymentRequestEntity r SET r.provisionLockedBy = :lockedBy, "
            + "r.provisionLockedUntil = :lockedUntil, "
            + "r.provisionAttemptCount = r.provisionAttemptCount + 1 "
            + "WHERE r.id = :id AND r.status = 'PROVISIONING' "
            + "AND r.provisionNextAttemptAt <= :now "
            + "AND (r.provisionLockedUntil IS NULL OR r.provisionLockedUntil < :now)")
    int claimProvisioning(
            @Param("id") String id,
            @Param("lockedBy") String lockedBy,
            @Param("lockedUntil") Instant lockedUntil,
            @Param("now") Instant now
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PaymentRequestEntity r SET r.cancelLockedBy = :lockedBy, "
            + "r.cancelLockedUntil = :lockedUntil, "
            + "r.cancelAttemptCount = r.cancelAttemptCount + 1 "
            + "WHERE r.id = :id AND r.status = 'CANCEL_PENDING' "
            + "AND r.cancelNextAttemptAt <= :now "
            + "AND (r.cancelLockedUntil IS NULL OR r.cancelLockedUntil < :now)")
    int claimCancellation(
            @Param("id") String id,
            @Param("lockedBy") String lockedBy,
            @Param("lockedUntil") Instant lockedUntil,
            @Param("now") Instant now
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PaymentRequestEntity r SET r.status = 'OPEN', r.paymentRequest = :paymentRequest, "
            + "r.invoiceId = :invoiceId, r.provisionLockedUntil = NULL, r.provisionLockedBy = NULL, "
            + "r.provisionLastError = NULL, r.provisionNextAttemptAt = NULL "
            + "WHERE r.id = :id AND r.status = 'PROVISIONING' AND r.provisionLockedBy = :lockedBy")
    int finalizeProvisioningOpen(
            @Param("id") String id,
            @Param("paymentRequest") String paymentRequest,
            @Param("invoiceId") String invoiceId,
            @Param("lockedBy") String lockedBy
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PaymentRequestEntity r SET r.status = 'CANCELLED', r.cancelledAt = :cancelledAt, "
            + "r.cancelLockedUntil = NULL, r.cancelLockedBy = NULL, r.cancelLastError = NULL, "
            + "r.cancelNextAttemptAt = NULL "
            + "WHERE r.id = :id AND r.status = 'CANCEL_PENDING' AND r.cancelLockedBy = :lockedBy")
    int finalizeCancelled(
            @Param("id") String id,
            @Param("cancelledAt") Instant cancelledAt,
            @Param("lockedBy") String lockedBy
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PaymentRequestEntity r SET r.status = 'FAILED', r.provisionLastError = :error, "
            + "r.provisionLockedUntil = NULL, r.provisionLockedBy = NULL, r.provisionNextAttemptAt = NULL "
            + "WHERE r.id = :id AND r.status = 'PROVISIONING' AND r.provisionLockedBy = :lockedBy")
    int markProvisioningFailed(
            @Param("id") String id,
            @Param("error") String error,
            @Param("lockedBy") String lockedBy
    );

    /**
     * Admin requeue: only FAILED → PROVISIONING. Concurrent PAID/OPEN/etc. are not overwritten.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PaymentRequestEntity r SET r.status = 'PROVISIONING', "
            + "r.provisionAttemptCount = 0, r.provisionNextAttemptAt = :now, "
            + "r.provisionLockedUntil = NULL, r.provisionLockedBy = NULL, r.provisionLastError = NULL "
            + "WHERE r.publicId = :publicId AND r.status = 'FAILED'")
    int requeueFailedProvisioning(@Param("publicId") String publicId, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PaymentRequestEntity r SET r.provisionLastError = :error, "
            + "r.provisionNextAttemptAt = :nextAttemptAt, "
            + "r.provisionLockedUntil = NULL, r.provisionLockedBy = NULL "
            + "WHERE r.id = :id AND r.status = 'PROVISIONING' AND r.provisionLockedBy = :lockedBy")
    int scheduleProvisioningRetry(
            @Param("id") String id,
            @Param("error") String error,
            @Param("nextAttemptAt") Instant nextAttemptAt,
            @Param("lockedBy") String lockedBy
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PaymentRequestEntity r SET r.cancelLastError = :error, "
            + "r.cancelNextAttemptAt = :nextAttemptAt, "
            + "r.cancelLockedUntil = NULL, r.cancelLockedBy = NULL "
            + "WHERE r.id = :id AND r.status = 'CANCEL_PENDING' AND r.cancelLockedBy = :lockedBy")
    int scheduleCancelRetry(
            @Param("id") String id,
            @Param("error") String error,
            @Param("nextAttemptAt") Instant nextAttemptAt,
            @Param("lockedBy") String lockedBy
    );

    long countByStatus(String status);

    @Query("SELECT COUNT(r) FROM PaymentRequestEntity r WHERE r.status = 'PROVISIONING' "
            + "AND r.provisionNextAttemptAt <= :now "
            + "AND (r.provisionLockedUntil IS NULL OR r.provisionLockedUntil < :now)")
    long countDueProvisioning(@Param("now") Instant now);

    @Query("SELECT COUNT(r) FROM PaymentRequestEntity r WHERE r.status = 'PROVISIONING' "
            + "AND r.provisionLockedUntil IS NOT NULL AND r.provisionLockedUntil >= :now")
    long countInProgressProvisioning(@Param("now") Instant now);

    @Query("SELECT COUNT(r) FROM PaymentRequestEntity r WHERE r.status = 'FAILED'")
    long countFailedProvisioning();

    @Query("SELECT COUNT(r) FROM PaymentRequestEntity r WHERE r.status = 'CANCEL_PENDING' "
            + "AND r.cancelNextAttemptAt <= :now "
            + "AND (r.cancelLockedUntil IS NULL OR r.cancelLockedUntil < :now)")
    long countDueCancellations(@Param("now") Instant now);

    @Query("SELECT COUNT(r) FROM PaymentRequestEntity r WHERE r.status = 'CANCEL_PENDING' "
            + "AND r.cancelLockedUntil IS NOT NULL AND r.cancelLockedUntil >= :now")
    long countInProgressCancellations(@Param("now") Instant now);

    @Query("SELECT COUNT(r) FROM PaymentRequestEntity r WHERE r.status = 'CANCEL_PENDING' "
            + "AND r.cancelAttemptCount >= :maxAttempts")
    long countExhaustedCancellations(@Param("maxAttempts") int maxAttempts);

    @Query("SELECT r FROM PaymentRequestEntity r WHERE r.status = 'FAILED' "
            + "ORDER BY r.createdAt DESC, r.id DESC")
    List<PaymentRequestEntity> findFailed(Pageable pageable);

    @Query("SELECT r FROM PaymentRequestEntity r WHERE r.status = 'CANCEL_PENDING' "
            + "AND r.cancelAttemptCount >= :maxAttempts "
            + "ORDER BY r.createdAt DESC, r.id DESC")
    List<PaymentRequestEntity> findExhaustedCancellations(
            @Param("maxAttempts") int maxAttempts,
            Pageable pageable
    );
}
