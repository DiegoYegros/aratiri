package com.aratiri.spark.application.port.out;

import com.aratiri.spark.domain.SparkWallet;

import java.util.Optional;

public interface SparkWalletPersistencePort {

    Optional<SparkWallet> findByUserId(String userId);

    Optional<SparkWallet> findByIdentityPublicKey(String identityPublicKey);

    SparkWallet save(SparkWallet wallet);

    void deleteByUserId(String userId);
}
