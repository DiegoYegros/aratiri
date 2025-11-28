package com.aratiri.infrastructure.persistence.jpa.entity;

import com.aratiri.transactions.application.dto.TransactionCurrency;
import com.aratiri.transactions.application.dto.TransactionType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

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
    }
}