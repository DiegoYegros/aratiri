package com.aratiri.infrastructure.persistence.jpa.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "OUTBOX_EVENTS")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEventEntity {

    private static final Duration RETRY_DELAY = Duration.ofSeconds(1);

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String aggregateType;

    @Column(nullable = false)
    private String aggregateId;

    @Column(nullable = false)
    private String eventType;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String payload;

    @CreationTimestamp
    private Instant createdAt;

    private Instant processedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private OutboxPublishStatus publishStatus = OutboxPublishStatus.PENDING;

    @Column(nullable = false)
    @Builder.Default
    private int publishAttempts = 0;

    @Column(columnDefinition = "TEXT")
    private String lastError;

    private Instant nextAttemptAt;

    public void markPublished(Instant publishedAt) {
        this.processedAt = publishedAt;
        this.publishStatus = OutboxPublishStatus.PUBLISHED;
        this.lastError = null;
        this.nextAttemptAt = null;
    }

    public void markPublishFailed(String errorMessage, Instant failedAt) {
        this.publishStatus = OutboxPublishStatus.FAILED;
        this.publishAttempts++;
        this.lastError = errorMessage;
        this.nextAttemptAt = failedAt.plus(RETRY_DELAY);
    }

    public void markInvalid(String errorMessage) {
        this.publishStatus = OutboxPublishStatus.INVALID;
        this.lastError = errorMessage;
        this.nextAttemptAt = null;
    }
}
