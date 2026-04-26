package com.aratiri.infrastructure.persistence.jpa.entity;

import com.aratiri.transactions.application.dto.TransactionCurrency;
import com.aratiri.transactions.application.dto.TransactionType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "TRANSACTIONS", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"reference_id", "user_id"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class TransactionEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "amount", nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private TransactionCurrency currency;

    @Column
    private String description;

    @Column(name = "reference_id", length = 64)
    private String referenceId;

    @Column(name = "current_status", nullable = false, length = 20)
    private String currentStatus;

    @Column(name = "current_amount", nullable = false)
    private long currentAmount;

    @Column(name = "balance_after")
    private Long balanceAfter;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "external_reference", length = 128)
    private String externalReference;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    @Column(name = "completed_at")
    private Instant completedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void ensureId() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.currentStatus == null) {
            this.currentStatus = "PENDING";
        }
        if (this.currentAmount == 0) {
            this.currentAmount = this.amount;
        }
    }
}
