package com.aratiri.infrastructure.persistence.jpa.repository;

import com.aratiri.infrastructure.persistence.jpa.entity.WebhookDeliveryEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.WebhookDeliveryStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WebhookDeliveryRepository extends JpaRepository<WebhookDeliveryEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT d FROM WebhookDeliveryEntity d
            WHERE d.status = com.aratiri.infrastructure.persistence.jpa.entity.WebhookDeliveryStatus.PENDING
            AND d.nextAttemptAt <= :now
            AND (d.lockedUntil IS NULL OR d.lockedUntil <= :now)
            ORDER BY d.nextAttemptAt ASC
            """)
    List<WebhookDeliveryEntity> findRunnableDeliveries(@Param("now") Instant now, Pageable pageable);

    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE WebhookDeliveryEntity d
            SET d.lockedBy = :lockedBy,
                d.lockedUntil = :lockedUntil,
                d.updatedAt = :now
            WHERE d.id = :id
              AND (d.lockedUntil IS NULL OR d.lockedUntil <= :now)
            """)
    int claimDelivery(@Param("id") UUID id,
                      @Param("lockedBy") String lockedBy,
                      @Param("lockedUntil") Instant lockedUntil,
                      @Param("now") Instant now);

    List<WebhookDeliveryEntity> findByEndpointIdOrderByCreatedAtDesc(UUID endpointId, Pageable pageable);

    List<WebhookDeliveryEntity> findByEndpointIdAndStatusOrderByCreatedAtDesc(UUID endpointId, WebhookDeliveryStatus status, Pageable pageable);

    @Query("SELECT d FROM WebhookDeliveryEntity d JOIN WebhookEventEntity e ON d.eventId = e.id WHERE d.endpointId = :endpointId AND e.eventType = :eventType ORDER BY d.createdAt DESC")
    List<WebhookDeliveryEntity> findByEndpointIdAndEventTypeOrderByCreatedAtDesc(@Param("endpointId") UUID endpointId, @Param("eventType") String eventType, Pageable pageable);

    @Query("SELECT d FROM WebhookDeliveryEntity d JOIN WebhookEventEntity e ON d.eventId = e.id WHERE d.endpointId = :endpointId AND d.status = :status AND e.eventType = :eventType ORDER BY d.createdAt DESC")
    List<WebhookDeliveryEntity> findByEndpointIdAndStatusAndEventTypeOrderByCreatedAtDesc(@Param("endpointId") UUID endpointId, @Param("status") WebhookDeliveryStatus status, @Param("eventType") String eventType, Pageable pageable);

    @Query("SELECT d FROM WebhookDeliveryEntity d WHERE d.endpointId = :endpointId ORDER BY d.createdAt DESC")
    List<WebhookDeliveryEntity> findByEndpointId(@Param("endpointId") UUID endpointId, Pageable pageable);

    @Query("SELECT d FROM WebhookDeliveryEntity d JOIN WebhookEventEntity e ON d.eventId = e.id WHERE d.status = :status AND e.eventType = :eventType ORDER BY d.createdAt DESC")
    List<WebhookDeliveryEntity> findByStatusAndEventTypeOrderByCreatedAtDesc(@Param("status") WebhookDeliveryStatus status, @Param("eventType") String eventType, Pageable pageable);

    List<WebhookDeliveryEntity> findByStatusOrderByCreatedAtDesc(WebhookDeliveryStatus status, Pageable pageable);

    @Query("SELECT d FROM WebhookDeliveryEntity d JOIN WebhookEventEntity e ON d.eventId = e.id WHERE e.eventType = :eventType ORDER BY d.createdAt DESC")
    List<WebhookDeliveryEntity> findByEventTypeOrderByCreatedAtDesc(@Param("eventType") String eventType, Pageable pageable);

    @Query("SELECT d FROM WebhookDeliveryEntity d JOIN FETCH WebhookEventEntity e ON d.eventId = e.id WHERE d.id = :id")
    Optional<WebhookDeliveryEntity> findByIdWithEvent(@Param("id") UUID id);
}
