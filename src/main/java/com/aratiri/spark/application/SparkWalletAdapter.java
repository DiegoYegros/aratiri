package com.aratiri.spark.application;

import com.aratiri.spark.application.dto.RegisterSparkWalletRequestDTO;
import com.aratiri.spark.application.dto.SparkWalletDTO;
import com.aratiri.spark.application.port.in.SparkWalletPort;
import com.aratiri.spark.application.port.out.SparkWalletPersistencePort;
import com.aratiri.spark.domain.SparkNetwork;
import com.aratiri.spark.domain.SparkWallet;
import com.aratiri.spark.domain.exception.SparkWalletConflictException;
import com.aratiri.spark.domain.exception.SparkWalletNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Metadata-only Spark wallet service. Stores and serves public wallet metadata
 * (identity public key, spark address, network, account index, UX flags). No
 * mnemonic, seed, or private key ever reaches this layer (decisions #1/#5).
 */
@Service
public class SparkWalletAdapter implements SparkWalletPort {

    private static final String WALLET_NOT_FOUND_MESSAGE = "Spark wallet not found";
    private static final String WALLET_ALREADY_REGISTERED_MESSAGE =
            "A Spark wallet is already registered for this user";
    private static final String IDENTITY_KEY_TAKEN_MESSAGE =
            "This identity public key is already registered to another user";

    private final SparkWalletPersistencePort persistencePort;
    private final Clock clock;

    public SparkWalletAdapter(SparkWalletPersistencePort persistencePort, Clock clock) {
        this.persistencePort = persistencePort;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SparkWalletDTO> get(String userId) {
        return persistencePort.findByUserId(userId).map(this::toDto);
    }

    @Override
    @Transactional
    public SparkWalletDTO register(String userId, RegisterSparkWalletRequestDTO request) {
        SparkNetwork network = SparkNetwork.parse(request.getNetwork());
        String identityPublicKey = request.getIdentityPublicKey().toLowerCase(Locale.ROOT);
        assertNoWalletForUser(userId);
        assertIdentityKeyFree(identityPublicKey);

        Instant now = clock.instant();
        SparkWallet wallet = new SparkWallet(
                UUID.randomUUID().toString(),
                userId,
                identityPublicKey,
                request.getSparkAddress().trim(),
                network,
                request.getAccountIndex(),
                false,
                false,
                now,
                now
        );
        try {
            return toDto(persistencePort.save(wallet));
        } catch (DataIntegrityViolationException e) {
            // Concurrent register race: the DB unique constraints are the backstop.
            throw new SparkWalletConflictException(
                    WALLET_ALREADY_REGISTERED_MESSAGE + " or " + IDENTITY_KEY_TAKEN_MESSAGE
            );
        }
    }

    @Override
    @Transactional
    public SparkWalletDTO setBackupVerified(String userId, boolean backupVerified) {
        SparkWallet wallet = getOwnedWallet(userId);
        return toDto(persistencePort.save(wallet.withBackupVerified(backupVerified, clock.instant())));
    }

    @Override
    @Transactional
    public SparkWalletDTO setPrivacyEnabled(String userId, boolean privacyEnabled) {
        SparkWallet wallet = getOwnedWallet(userId);
        return toDto(persistencePort.save(wallet.withPrivacyEnabled(privacyEnabled, clock.instant())));
    }

    @Override
    @Transactional
    public void forget(String userId) {
        persistencePort.deleteByUserId(userId);
    }

    private void assertNoWalletForUser(String userId) {
        if (persistencePort.findByUserId(userId).isPresent()) {
            throw new SparkWalletConflictException(WALLET_ALREADY_REGISTERED_MESSAGE);
        }
    }

    private void assertIdentityKeyFree(String identityPublicKey) {
        if (persistencePort.findByIdentityPublicKey(identityPublicKey).isPresent()) {
            throw new SparkWalletConflictException(IDENTITY_KEY_TAKEN_MESSAGE);
        }
    }

    private SparkWallet getOwnedWallet(String userId) {
        return persistencePort.findByUserId(userId)
                .orElseThrow(() -> new SparkWalletNotFoundException(WALLET_NOT_FOUND_MESSAGE));
    }

    private SparkWalletDTO toDto(SparkWallet wallet) {
        return SparkWalletDTO.builder()
                .sparkAddress(wallet.sparkAddress())
                .identityPublicKey(wallet.identityPublicKey())
                .network(wallet.network().name())
                .accountIndex(wallet.accountIndex())
                .backupVerified(wallet.backupVerified())
                .privacyEnabled(wallet.privacyEnabled())
                .build();
    }
}
