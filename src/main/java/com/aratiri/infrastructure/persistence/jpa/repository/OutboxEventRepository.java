package com.aratiri.infrastructure.persistence.jpa.repository;

import com.aratiri.infrastructure.persistence.jpa.entity.OutboxEventEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.OutboxPublishStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {
    boolean existsByAggregateTypeAndAggregateIdAndEventType(String aggregateType, String aggregateId, String eventType);

    @Query(value = """
            SELECT * FROM aratiri.outbox_events
            WHERE processed_at IS NULL
              AND publish_status IN ('PENDING', 'FAILED')
              AND (next_attempt_at IS NULL OR next_attempt_at <= :now)
              AND (locked_until IS NULL OR locked_until <= :now)
            ORDER BY created_at ASC
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEventEntity> lockClaimableEvents(
            @Param("now") Instant now,
            @Param("batchSize") int batchSize
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE OutboxEventEntity e
            SET e.processedAt = :publishedAt,
                e.publishStatus = :published,
                e.lastError = NULL,
                e.nextAttemptAt = NULL,
                e.lockedBy = NULL,
                e.lockedUntil = NULL
            WHERE e.id = :id
              AND e.lockedBy = :lockedBy
              AND e.publishStatus IN :statuses
              AND e.processedAt IS NULL
            """)
    int markPublished(
            @Param("id") UUID id,
            @Param("lockedBy") String lockedBy,
            @Param("publishedAt") Instant publishedAt,
            @Param("published") OutboxPublishStatus published,
            @Param("statuses") Collection<OutboxPublishStatus> statuses
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE OutboxEventEntity e
            SET e.publishStatus = :failed,
                e.publishAttempts = e.publishAttempts + 1,
                e.lastError = :errorMessage,
                e.nextAttemptAt = :nextAttemptAt,
                e.lockedBy = NULL,
                e.lockedUntil = NULL
            WHERE e.id = :id
              AND e.lockedBy = :lockedBy
              AND e.publishStatus IN :statuses
              AND e.processedAt IS NULL
            """)
    int markPublishFailed(
            @Param("id") UUID id,
            @Param("lockedBy") String lockedBy,
            @Param("errorMessage") String errorMessage,
            @Param("nextAttemptAt") Instant nextAttemptAt,
            @Param("failed") OutboxPublishStatus failed,
            @Param("statuses") Collection<OutboxPublishStatus> statuses
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE OutboxEventEntity e
            SET e.publishStatus = :invalid,
                e.lastError = :errorMessage,
                e.nextAttemptAt = NULL,
                e.lockedBy = NULL,
                e.lockedUntil = NULL
            WHERE e.id = :id
              AND e.lockedBy = :lockedBy
              AND e.publishStatus IN :statuses
              AND e.processedAt IS NULL
            """)
    int markInvalid(
            @Param("id") UUID id,
            @Param("lockedBy") String lockedBy,
            @Param("errorMessage") String errorMessage,
            @Param("invalid") OutboxPublishStatus invalid,
            @Param("statuses") Collection<OutboxPublishStatus> statuses
    );
}
