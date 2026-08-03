package com.aratiri.spark.application.port.in;

import com.aratiri.spark.application.dto.RegisterSparkWalletRequestDTO;
import com.aratiri.spark.application.dto.SparkWalletDTO;

import java.util.Optional;

/**
 * User-scoped Spark wallet metadata operations. Keys, signing, balances and
 * history live in the browser; this port is metadata-only by design (#5).
 */
public interface SparkWalletPort {

    Optional<SparkWalletDTO> get(String userId);

    SparkWalletDTO register(String userId, RegisterSparkWalletRequestDTO request);

    SparkWalletDTO setBackupVerified(String userId, boolean backupVerified);

    SparkWalletDTO setPrivacyEnabled(String userId, boolean privacyEnabled);

    void forget(String userId);
}
