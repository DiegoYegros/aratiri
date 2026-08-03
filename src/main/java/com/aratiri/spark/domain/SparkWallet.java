package com.aratiri.spark.domain;

import java.time.Instant;

/**
 * Non-custodial Spark wallet metadata. Holds only public values: the identity
 * public key (compressed secp256k1 hex), the derived spark address, network,
 * account index, and the two UX flags {@code backupVerified} / {@code privacyEnabled}.
 * The mnemonic and all signing material live exclusively in the browser.
 */
public record SparkWallet(
        String id,
        String userId,
        String identityPublicKey,
        String sparkAddress,
        SparkNetwork network,
        int accountIndex,
        boolean backupVerified,
        boolean privacyEnabled,
        Instant createdAt,
        Instant updatedAt
) {
    public SparkWallet withBackupVerified(boolean backupVerified, Instant now) {
        return new SparkWallet(
                id, userId, identityPublicKey, sparkAddress, network, accountIndex,
                backupVerified, privacyEnabled, createdAt, now
        );
    }

    public SparkWallet withPrivacyEnabled(boolean privacyEnabled, Instant now) {
        return new SparkWallet(
                id, userId, identityPublicKey, sparkAddress, network, accountIndex,
                backupVerified, privacyEnabled, createdAt, now
        );
    }
}
