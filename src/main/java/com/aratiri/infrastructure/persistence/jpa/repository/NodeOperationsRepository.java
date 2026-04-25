package com.aratiri.infrastructure.persistence.jpa.repository;

import com.aratiri.infrastructure.persistence.jpa.entity.NodeOperationEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.NodeOperationStatus;
import com.aratiri.infrastructure.persistence.jpa.entity.NodeOperationType;
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
import java.util.UUID;

public interface NodeOperationsRepository extends JpaRepository<NodeOperationEntity, UUID> {

    Optional<NodeOperationEntity> findByTransactionId(String transactionId);

    boolean existsByTransactionId(String transactionId);

    @Query(value = """
            SELECT n FROM NodeOperationEntity n
            WHERE n.status = :status
              AND n.nextAttemptAt <= :now
            ORDER BY n.createdAt ASC
            """)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<NodeOperationEntity> findPendingDueOperations(
            @Param("status") NodeOperationStatus status,
            @Param("now") Instant now,
            Pageable pageable
    );

    @Query(value = """
            SELECT n FROM NodeOperationEntity n
            WHERE n.status = :status
              AND n.lockedUntil < :now
            ORDER BY n.createdAt ASC
            """)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<NodeOperationEntity> findStaleInProgressOperations(
            @Param("status") NodeOperationStatus status,
            @Param("now") Instant now,
            Pageable pageable
    );

    @Query(value = """
            SELECT n FROM NodeOperationEntity n
            WHERE n.status = :status
            ORDER BY n.createdAt ASC
            """)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<NodeOperationEntity> findBroadcastedOperations(
            @Param("status") NodeOperationStatus status,
            Pageable pageable
    );

    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE NodeOperationEntity n
            SET n.status = :newStatus,
                n.attemptCount = n.attemptCount + 1,
                n.lockedBy = :lockedBy,
                n.lockedUntil = :lockedUntil,
                n.updatedAt = :now
            WHERE n.id = :id
              AND n.status = :expectedStatus
            """)
    int claimPendingOperation(
            @Param("id") UUID id,
            @Param("newStatus") NodeOperationStatus newStatus,
            @Param("lockedBy") String lockedBy,
            @Param("lockedUntil") Instant lockedUntil,
            @Param("now") Instant now,
            @Param("expectedStatus") NodeOperationStatus expectedStatus
    );

    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE NodeOperationEntity n
            SET n.status = :newStatus,
                n.attemptCount = n.attemptCount + 1,
                n.lockedBy = :lockedBy,
                n.lockedUntil = :lockedUntil,
                n.updatedAt = :now
            WHERE n.id = :id
              AND n.status = :expectedStatus
              AND n.lockedUntil < :now
            """)
    int claimStaleOperation(
            @Param("id") UUID id,
            @Param("newStatus") NodeOperationStatus newStatus,
            @Param("lockedBy") String lockedBy,
            @Param("lockedUntil") Instant lockedUntil,
            @Param("now") Instant now,
            @Param("expectedStatus") NodeOperationStatus expectedStatus
    );

    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE NodeOperationEntity n
            SET n.status = :newStatus,
                n.attemptCount = n.attemptCount + 1,
                n.lockedBy = :lockedBy,
                n.lockedUntil = :lockedUntil,
                n.updatedAt = :now
            WHERE n.id = :id
              AND n.status = :expectedStatus
            """)
    int claimBroadcastedOperation(
            @Param("id") UUID id,
            @Param("newStatus") NodeOperationStatus newStatus,
            @Param("lockedBy") String lockedBy,
            @Param("lockedUntil") Instant lockedUntil,
            @Param("now") Instant now,
            @Param("expectedStatus") NodeOperationStatus expectedStatus
    );

    List<NodeOperationEntity> findByStatus(NodeOperationStatus status, Pageable pageable);

    @Query("""
            SELECT n FROM NodeOperationEntity n
            WHERE (:status IS NULL OR n.status = :status)
              AND (:type IS NULL OR n.operationType = :type)
              AND (:transactionId IS NULL OR n.transactionId = :transactionId)
            ORDER BY n.createdAt DESC
            """)
    List<NodeOperationEntity> findByFilters(
            @Param("status") NodeOperationStatus status,
            @Param("type") NodeOperationType type,
            @Param("transactionId") String transactionId,
            Pageable pageable
    );
}
