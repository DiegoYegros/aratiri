package com.aratiri.spark.infrastructure;

import com.aratiri.infrastructure.persistence.jpa.entity.SparkWalletEntity;
import com.aratiri.infrastructure.persistence.jpa.repository.SparkWalletRepository;
import com.aratiri.spark.application.port.out.SparkWalletPersistencePort;
import com.aratiri.spark.domain.SparkNetwork;
import com.aratiri.spark.domain.SparkWallet;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
public class SparkWalletPersistenceAdapter implements SparkWalletPersistencePort {

    private final SparkWalletRepository repository;

    public SparkWalletPersistenceAdapter(SparkWalletRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<SparkWallet> findByUserId(String userId) {
        return repository.findByUserId(userId).map(this::toDomain);
    }

    @Override
    public Optional<SparkWallet> findByIdentityPublicKey(String identityPublicKey) {
        return repository.findByIdentityPublicKey(identityPublicKey).map(this::toDomain);
    }

    @Override
    @Transactional
    public SparkWallet save(SparkWallet wallet) {
        // saveAndFlush surfaces unique-constraint violations deterministically so
        // the application layer can translate register races into a 409.
        return toDomain(repository.saveAndFlush(toEntity(wallet)));
    }

    @Override
    @Transactional
    public void deleteByUserId(String userId) {
        repository.deleteByUserId(userId);
    }

    private SparkWalletEntity toEntity(SparkWallet wallet) {
        return SparkWalletEntity.builder()
                .id(wallet.id())
                .userId(wallet.userId())
                .identityPublicKey(wallet.identityPublicKey())
                .sparkAddress(wallet.sparkAddress())
                .network(wallet.network().name())
                .accountIndex(wallet.accountIndex())
                .backupVerified(wallet.backupVerified())
                .privacyEnabled(wallet.privacyEnabled())
                .createdAt(wallet.createdAt())
                .updatedAt(wallet.updatedAt())
                .build();
    }

    private SparkWallet toDomain(SparkWalletEntity entity) {
        return new SparkWallet(
                entity.getId(),
                entity.getUserId(),
                entity.getIdentityPublicKey(),
                entity.getSparkAddress(),
                SparkNetwork.valueOf(entity.getNetwork()),
                entity.getAccountIndex(),
                entity.isBackupVerified(),
                entity.isPrivacyEnabled(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
