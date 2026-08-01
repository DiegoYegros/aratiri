package com.aratiri.infrastructure.persistence.jpa.repository;

import com.aratiri.infrastructure.persistence.jpa.entity.PaymentRequestEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
     * Marks a linked request PAID. Wins over OPEN and CANCELLED so a real
     * invoice settlement is never suppressed by a cancel/expiry race.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PaymentRequestEntity r SET r.status = 'PAID', r.paidAt = :paidAt "
            + "WHERE r.paymentHash = :paymentHash AND r.status IN ('OPEN', 'CANCELLED')")
    int markPaidByPaymentHash(@Param("paymentHash") String paymentHash, @Param("paidAt") Instant paidAt);

    /**
     * Cancels only while still stored as OPEN. Callers must interpret zero rows
     * against current state (PAID conflict vs already CANCELLED idempotent).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PaymentRequestEntity r SET r.status = 'CANCELLED', r.cancelledAt = :cancelledAt "
            + "WHERE r.publicId = :publicId AND r.userId = :userId AND r.status = 'OPEN' "
            + "AND r.expiresAt > :now")
    int cancelIfOpen(
            @Param("publicId") String publicId,
            @Param("userId") String userId,
            @Param("cancelledAt") Instant cancelledAt,
            @Param("now") Instant now
    );
}
