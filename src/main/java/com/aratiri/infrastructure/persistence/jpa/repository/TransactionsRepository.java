package com.aratiri.infrastructure.persistence.jpa.repository;

import com.aratiri.admin.application.dto.TransactionStatsDTO;
import com.aratiri.infrastructure.persistence.jpa.entity.TransactionEntity;
import com.aratiri.transactions.application.dto.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TransactionsRepository extends JpaRepository<TransactionEntity, String> {
    @Query("SELECT t FROM TransactionEntity t WHERE t.userId = :userId " +
            "AND t.createdAt >= :from AND t.createdAt <= :to " +
            "ORDER BY t.createdAt DESC")
    List<TransactionEntity> findByUserIdAndCreatedAtBetween(
            @Param("userId") String userId,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    @Query("SELECT t FROM TransactionEntity t WHERE t.userId = :userId " +
            "AND (t.createdAt < :cursorCreatedAt OR (t.createdAt = :cursorCreatedAt AND t.id < :cursorId)) " +
            "ORDER BY t.createdAt DESC, t.id DESC")
    List<TransactionEntity> findByUserIdWithCursor(
            @Param("userId") String userId,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") String cursorId,
            org.springframework.data.domain.Pageable pageable
    );

    @Query("SELECT t FROM TransactionEntity t WHERE t.userId = :userId " +
            "ORDER BY t.createdAt DESC, t.id DESC")
    List<TransactionEntity> findByUserIdOrderByCreatedAtDescIdDesc(
            @Param("userId") String userId,
            org.springframework.data.domain.Pageable pageable
    );

    boolean existsByReferenceId(String referenceId);

    Optional<TransactionEntity> findFirstByReferenceIdOrderByCreatedAtDesc(String referenceId);

    Optional<TransactionEntity> findFirstByUserIdAndReferenceIdAndTypeOrderByCreatedAtDesc(
            String userId,
            String referenceId,
            TransactionType type
    );

    @Query("SELECT t FROM TransactionEntity t WHERE t.type = com.aratiri.transactions.application.dto.TransactionType.LIGHTNING_DEBIT " +
            "AND t.referenceId IS NOT NULL " +
            "AND t.createdAt < :timestamp " +
            "AND EXISTS (SELECT pending FROM TransactionEventEntity pending WHERE pending.transaction = t " +
            "AND pending.eventType = com.aratiri.infrastructure.persistence.jpa.entity.TransactionEventType.STATUS_CHANGED " +
            "AND pending.status = com.aratiri.transactions.application.dto.TransactionStatus.PENDING) " +
            "AND NOT EXISTS (SELECT e FROM TransactionEventEntity e WHERE e.transaction = t " +
            "AND e.eventType = com.aratiri.infrastructure.persistence.jpa.entity.TransactionEventType.STATUS_CHANGED " +
            "AND e.status IN (com.aratiri.transactions.application.dto.TransactionStatus.COMPLETED, com.aratiri.transactions.application.dto.TransactionStatus.FAILED))")
    List<TransactionEntity> findPendingTransactionsOlderThan(@Param("timestamp") Instant timestamp);

    @Query("SELECT new com.aratiri.admin.application.dto.TransactionStatsDTO(" +
            "CAST(t.createdAt AS LocalDate), " +
            "CASE WHEN t.type LIKE '%CREDIT%' THEN 'credit' ELSE 'debit' END, " +
            "CAST(SUM(t.amount + COALESCE((SELECT SUM(f.amountDelta) FROM TransactionEventEntity f WHERE f.transaction = t " +
            "AND f.eventType = com.aratiri.infrastructure.persistence.jpa.entity.TransactionEventType.FEE_ADDED), 0)) AS java.math.BigDecimal) / CAST(100000000 AS java.math.BigDecimal), " +
            "COUNT(t)) " +
            "FROM TransactionEntity t " +
            "WHERE t.createdAt >= :from AND t.createdAt <= :to " +
            "AND EXISTS (SELECT completed FROM TransactionEventEntity completed WHERE completed.transaction = t " +
            "AND completed.eventType = com.aratiri.infrastructure.persistence.jpa.entity.TransactionEventType.STATUS_CHANGED " +
            "AND completed.status = com.aratiri.transactions.application.dto.TransactionStatus.COMPLETED) " +
            "GROUP BY CAST(t.createdAt AS LocalDate), CASE WHEN t.type LIKE '%CREDIT%' THEN 'credit' ELSE 'debit' END " +
            "ORDER BY CAST(t.createdAt AS LocalDate)")
    List<TransactionStatsDTO> findTransactionStats(@Param("from") Instant from, @Param("to") Instant to);
}
