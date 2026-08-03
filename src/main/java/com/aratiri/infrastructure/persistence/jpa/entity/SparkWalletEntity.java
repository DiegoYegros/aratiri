package com.aratiri.infrastructure.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Non-custodial Spark wallet metadata. Stores ONLY public values — never a
 * mnemonic, seed, or private key. One row per user ({@code user_id} unique).
 */
@Entity
@Table(name = "spark_wallets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SparkWalletEntity {

    @Id
    @Setter(AccessLevel.NONE)
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, length = 36, unique = true)
    private String userId;

    @Column(name = "identity_public_key", nullable = false, length = 66, unique = true)
    private String identityPublicKey;

    @Column(name = "spark_address", nullable = false, length = 128)
    private String sparkAddress;

    @Column(name = "network", nullable = false, length = 16)
    private String network;

    @Column(name = "account_index", nullable = false)
    private int accountIndex;

    @Column(name = "backup_verified", nullable = false)
    private boolean backupVerified;

    @Column(name = "privacy_enabled", nullable = false)
    private boolean privacyEnabled;

    @Setter(AccessLevel.NONE)
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
