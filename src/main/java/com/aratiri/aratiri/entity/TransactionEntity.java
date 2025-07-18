package com.aratiri.aratiri.entity;

import com.aratiri.aratiri.enums.TransactionCurrency;
import com.aratiri.aratiri.enums.TransactionStatus;
import com.aratiri.aratiri.enums.TransactionType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "TRANSACTIONS")
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

    @Column(name = "amount", nullable = false, precision = 20, scale = 8)
    private BigDecimal amount;

    @Column(name = "balance_after", precision = 20, scale = 8)
    private BigDecimal balanceAfter;

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private TransactionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private TransactionCurrency currency;

    @Column(length = 255)
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
        if (this.createdAt == null){
            this.createdAt = Instant.now();
        }
    }
}