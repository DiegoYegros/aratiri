package com.aratiri.aratiri.repository;

import com.aratiri.aratiri.entity.TransactionEntity;
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
}
