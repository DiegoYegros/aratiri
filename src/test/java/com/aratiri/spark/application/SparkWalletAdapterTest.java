package com.aratiri.spark.application;

import com.aratiri.errors.ApplicationException;
import com.aratiri.spark.application.dto.RegisterSparkWalletRequestDTO;
import com.aratiri.spark.application.dto.SparkWalletDTO;
import com.aratiri.spark.application.port.out.SparkWalletPersistencePort;
import com.aratiri.spark.domain.SparkNetwork;
import com.aratiri.spark.domain.SparkWallet;
import com.aratiri.spark.domain.exception.SparkWalletConflictException;
import com.aratiri.spark.domain.exception.SparkWalletNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SparkWalletAdapterTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String USER_ID = "user-1";
    private static final String IDENTITY_KEY =
            "02aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899";
    private static final String SPARK_ADDRESS = "sparkrt1qphk9clx4jh5gp0wjkwsc3h28m3y3n4m2v2a8j7v5q3z2";

    @Mock
    private SparkWalletPersistencePort persistencePort;

    private SparkWalletAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new SparkWalletAdapter(persistencePort, CLOCK);
    }

    @Test
    void get_returnsEmptyWhenNoWallet() {
        when(persistencePort.findByUserId(USER_ID)).thenReturn(Optional.empty());
        assertTrue(adapter.get(USER_ID).isEmpty());
    }

    @Test
    void get_returnsDtoWhenWalletExists() {
        when(persistencePort.findByUserId(USER_ID)).thenReturn(Optional.of(wallet(false, false)));
        SparkWalletDTO dto = adapter.get(USER_ID).orElseThrow();
        assertEquals(SPARK_ADDRESS, dto.getSparkAddress());
        assertEquals(IDENTITY_KEY, dto.getIdentityPublicKey());
        assertEquals("REGTEST", dto.getNetwork());
        assertEquals(0, dto.getAccountIndex());
        assertFalse(dto.isBackupVerified());
        assertFalse(dto.isPrivacyEnabled());
    }

    @Test
    void register_savesWalletWithDefaults() {
        when(persistencePort.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(persistencePort.findByIdentityPublicKey(IDENTITY_KEY)).thenReturn(Optional.empty());
        when(persistencePort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SparkWalletDTO dto = adapter.register(USER_ID, request());

        assertEquals(SPARK_ADDRESS, dto.getSparkAddress());
        assertEquals(IDENTITY_KEY, dto.getIdentityPublicKey());
        assertEquals("REGTEST", dto.getNetwork());
        assertEquals(0, dto.getAccountIndex());
        assertFalse(dto.isBackupVerified());
        assertFalse(dto.isPrivacyEnabled());
        verify(persistencePort).save(any());
    }

    @Test
    void register_conflictsWhenUserAlreadyHasWallet() {
        when(persistencePort.findByUserId(USER_ID)).thenReturn(Optional.of(wallet(false, false)));
        assertThrows(SparkWalletConflictException.class, () -> adapter.register(USER_ID, request()));
        verify(persistencePort, never()).save(any());
    }

    @Test
    void register_conflictsWhenIdentityKeyAlreadyRegistered() {
        when(persistencePort.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(persistencePort.findByIdentityPublicKey(IDENTITY_KEY)).thenReturn(Optional.of(wallet(false, false)));
        assertThrows(SparkWalletConflictException.class, () -> adapter.register(USER_ID, request()));
        verify(persistencePort, never()).save(any());
    }

    @Test
    void register_translatesRaceViolationToConflict() {
        when(persistencePort.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(persistencePort.findByIdentityPublicKey(IDENTITY_KEY)).thenReturn(Optional.empty());
        when(persistencePort.save(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));
        assertThrows(SparkWalletConflictException.class, () -> adapter.register(USER_ID, request()));
    }

    @Test
    void register_normalizesIdentityKeyToLowercase() {
        when(persistencePort.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(persistencePort.findByIdentityPublicKey(IDENTITY_KEY)).thenReturn(Optional.empty());
        when(persistencePort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        RegisterSparkWalletRequestDTO request = request();
        request.setIdentityPublicKey(IDENTITY_KEY.toUpperCase());
        SparkWalletDTO dto = adapter.register(USER_ID, request);

        assertEquals(IDENTITY_KEY, dto.getIdentityPublicKey());
        verify(persistencePort).findByIdentityPublicKey(IDENTITY_KEY);
    }

    @Test
    void register_rejectsUnknownNetwork() {
        RegisterSparkWalletRequestDTO request = request();
        request.setNetwork("LOCAL");
        assertThrows(ApplicationException.class, () -> adapter.register(USER_ID, request));
    }

    @Test
    void setBackupVerified_updatesFlag() {
        when(persistencePort.findByUserId(USER_ID)).thenReturn(Optional.of(wallet(false, false)));
        when(persistencePort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SparkWalletDTO dto = adapter.setBackupVerified(USER_ID, true);

        assertTrue(dto.isBackupVerified());
        assertFalse(dto.isPrivacyEnabled());
    }

    @Test
    void setBackupVerified_notFound() {
        when(persistencePort.findByUserId(USER_ID)).thenReturn(Optional.empty());
        assertThrows(SparkWalletNotFoundException.class, () -> adapter.setBackupVerified(USER_ID, true));
    }

    @Test
    void setPrivacyEnabled_updatesFlag() {
        when(persistencePort.findByUserId(USER_ID)).thenReturn(Optional.of(wallet(false, false)));
        when(persistencePort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SparkWalletDTO dto = adapter.setPrivacyEnabled(USER_ID, true);

        assertTrue(dto.isPrivacyEnabled());
        assertFalse(dto.isBackupVerified());
    }

    @Test
    void setPrivacyEnabled_notFound() {
        when(persistencePort.findByUserId(USER_ID)).thenReturn(Optional.empty());
        assertThrows(SparkWalletNotFoundException.class, () -> adapter.setPrivacyEnabled(USER_ID, true));
    }

    @Test
    void forget_deletesByUserId() {
        adapter.forget(USER_ID);
        verify(persistencePort).deleteByUserId(USER_ID);
    }

    private static SparkWallet wallet(boolean backupVerified, boolean privacyEnabled) {
        return new SparkWallet(
                "wallet-1",
                USER_ID,
                IDENTITY_KEY,
                SPARK_ADDRESS,
                SparkNetwork.REGTEST,
                0,
                backupVerified,
                privacyEnabled,
                NOW,
                NOW
        );
    }

    private static RegisterSparkWalletRequestDTO request() {
        RegisterSparkWalletRequestDTO dto = new RegisterSparkWalletRequestDTO();
        dto.setIdentityPublicKey(IDENTITY_KEY);
        dto.setSparkAddress(SPARK_ADDRESS);
        dto.setNetwork("REGTEST");
        dto.setAccountIndex(0);
        return dto;
    }
}
