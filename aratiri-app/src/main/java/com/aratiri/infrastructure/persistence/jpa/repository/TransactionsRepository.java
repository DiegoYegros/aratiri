package com.aratiri.infrastructure.persistence.jpa.repository;

import com.aratiri.dto.admin.TransactionStatsDTO;
import com.aratiri.infrastructure.persistence.jpa.entity.TransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface TransactionsRepository extends JpaRepository<TransactionEntity, String> {
    @Query("SELECT t FROM TransactionEntity t WHERE t.userId = :userId " +
            "AND t.createdAt >= :from AND t.createdAt <= :to " +
            "ORDER BY t.createdAt DESC")
    List<TransactionEntity> findByUserIdAndCreatedAtBetween(
            @Param("userId") String userId,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    boolean existsByReferenceId(String referenceId);

    @Query("SELECT t FROM TransactionEntity t WHERE t.status = 'PENDING' AND t.createdAt < :timestamp")
    List<TransactionEntity> findPendingTransactionsOlderThan(@Param("timestamp") Instant timestamp);

    @Query("SELECT new com.aratiri.dto.admin.TransactionStatsDTO(" +
            "CAST(t.createdAt AS LocalDate), " +
            "CASE WHEN t.type LIKE '%CREDIT%' THEN 'credit' ELSE 'debit' END, " +
            "SUM(t.amount), " +
            "COUNT(t)) " +
            "FROM TransactionEntity t " +
            "WHERE t.createdAt >= :from AND t.createdAt <= :to AND t.status = 'COMPLETED' " +
            "GROUP BY CAST(t.createdAt AS LocalDate), CASE WHEN t.type LIKE '%CREDIT%' THEN 'credit' ELSE 'debit' END " +
            "ORDER BY CAST(t.createdAt AS LocalDate)")
    List<TransactionStatsDTO> findTransactionStats(@Param("from") Instant from, @Param("to") Instant to);
}
