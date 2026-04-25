package com.aratiri.infrastructure.persistence.jpa.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "NODE_OPERATIONS")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeOperationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "transaction_id", nullable = false, unique = true, length = 36)
    private String transactionId;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false, length = 40)
    private NodeOperationType operationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private NodeOperationStatus status;

    @Column(name = "reference_id", length = 128)
    private String referenceId;

    @Column(name = "request_payload", columnDefinition = "TEXT", nullable = false)
    private String requestPayload;

    @Column(name = "external_id", length = 128)
    private String externalId;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "locked_by", length = 128)
    private String lockedBy;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @PrePersist
    public void ensureDefaults() {
        if (this.attemptCount == null) {
            this.attemptCount = 0;
        }
        if (this.nextAttemptAt == null) {
            this.nextAttemptAt = Instant.now();
        }
        if (this.status == null) {
            this.status = NodeOperationStatus.PENDING;
        }
    }
}
