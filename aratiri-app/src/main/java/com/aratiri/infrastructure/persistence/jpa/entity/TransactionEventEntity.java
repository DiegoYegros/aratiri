package com.aratiri.infrastructure.persistence.jpa.entity;

import com.aratiri.transactions.application.dto.TransactionStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "TRANSACTION_EVENTS")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private TransactionEntity transaction;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private TransactionEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private TransactionStatus status;

    @Column(name = "amount_delta", precision = 20, scale = 8)
    private BigDecimal amountDelta;

    @Column(name = "balance_after", precision = 20, scale = 8)
    private BigDecimal balanceAfter;

    @Column(name = "details")
    private String details;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
