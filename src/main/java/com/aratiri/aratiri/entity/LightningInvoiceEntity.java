package com.aratiri.aratiri.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;


@Table(name = "LIGHTNING_INVOICES")
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LightningInvoiceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private String id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "payment_hash", nullable = false, length = 64)
    private String paymentHash;

    @Column(name = "preimage", nullable = false, length = 64)
    private String preimage;

    @Column(name = "payment_request", nullable = false, length = 1000)
    private String paymentRequest;

    @Enumerated(EnumType.STRING)
    @Column(name = "invoice_state", nullable = false, length = 20)
    private InvoiceState invoiceState;

    @Column(name = "amount_sats", nullable = false)
    private Long amountSats;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expiry", nullable = false)
    private long expiry;

    @Column(name = "amt_paid_sats", nullable = false)
    private long amountPaidSats;

    @Column(name = "settled_at")
    private LocalDateTime settledAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id", insertable = false, updatable = false)
    private UserEntity user;

    @Column(name = "memo", length = 500)
    private String memo;

    public enum InvoiceState {
        OPEN,
        ACCEPTED,
        SETTLED,
        CANCELED
    }
}