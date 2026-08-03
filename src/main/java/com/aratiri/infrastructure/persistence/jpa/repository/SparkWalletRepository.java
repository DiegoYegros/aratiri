package com.aratiri.infrastructure.persistence.jpa.repository;

import com.aratiri.infrastructure.persistence.jpa.entity.SparkWalletEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SparkWalletRepository extends JpaRepository<SparkWalletEntity, String> {

    Optional<SparkWalletEntity> findByUserId(String userId);

    Optional<SparkWalletEntity> findByIdentityPublicKey(String identityPublicKey);

    void deleteByUserId(String userId);
}
