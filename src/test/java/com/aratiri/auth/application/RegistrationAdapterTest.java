package com.aratiri.auth.application;

import com.aratiri.accounts.application.port.in.AccountsPort;
import com.aratiri.auth.application.port.in.RegistrationCommand;
import com.aratiri.auth.application.port.in.VerificationCommand;
import com.aratiri.auth.application.port.out.AccessTokenPort;
import com.aratiri.auth.application.port.out.EmailNotificationPort;
import com.aratiri.auth.application.port.out.LoadUserPort;
import com.aratiri.auth.application.port.out.PasswordEncoderPort;
import com.aratiri.auth.application.port.out.RefreshTokenPort;
import com.aratiri.auth.application.port.out.RegistrationDraftPort;
import com.aratiri.auth.application.port.out.UserCommandPort;
import com.aratiri.auth.domain.AuthProvider;
import com.aratiri.auth.domain.AuthTokens;
import com.aratiri.auth.domain.AuthUser;
import com.aratiri.auth.domain.RefreshToken;
import com.aratiri.auth.domain.RegistrationDraft;
import com.aratiri.auth.domain.Role;
import com.aratiri.shared.exception.AratiriException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationAdapterTest {

    @Mock
    private RegistrationDraftPort registrationDraftPort;

    @Mock
    private EmailNotificationPort emailNotificationPort;

    @Mock
    private PasswordEncoderPort passwordEncoderPort;

    @Mock
    private UserCommandPort userCommandPort;

    @Mock
    private AccountsPort accountsPort;

    @Mock
    private AccessTokenPort accessTokenPort;

    @Mock
    private RefreshTokenPort refreshTokenPort;

    @Mock
    private LoadUserPort loadUserPort;

    private Clock clock = Clock.fixed(Instant.parse("2025-01-01T12:00:00Z"), ZoneId.of("UTC"));

    private RegistrationAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new RegistrationAdapter(
                registrationDraftPort, emailNotificationPort, passwordEncoderPort,
                userCommandPort, accountsPort, accessTokenPort, refreshTokenPort,
                loadUserPort, clock);
    }

    @Test
    void initiateRegistration_createsDraftAndSendsEmail() {
        RegistrationCommand command = new RegistrationCommand("Test User", "test@test.com", "password", null);
        when(loadUserPort.findByEmail("test@test.com")).thenReturn(Optional.empty());
        when(passwordEncoderPort.encode("password")).thenReturn("encoded");

        adapter.initiateRegistration(command);

        verify(registrationDraftPort).save(any(RegistrationDraft.class));
        verify(emailNotificationPort).sendVerificationEmail(eq("test@test.com"), anyString());
    }

    @Test
    void initiateRegistration_throwsWhenEmailInUse() {
        RegistrationCommand command = new RegistrationCommand("Test User", "test@test.com", "password", null);
        AuthUser existing = new AuthUser("user-1", "Test", "test@test.com", AuthProvider.LOCAL, Role.USER);
        when(loadUserPort.findByEmail("test@test.com")).thenReturn(Optional.of(existing));

        assertThrows(AratiriException.class, () -> adapter.initiateRegistration(command));
        verify(registrationDraftPort, never()).save(any());
    }

    @Test
    void initiateRegistration_throwsWhenAliasInUse() {
        RegistrationCommand command = new RegistrationCommand("Test User", "test@test.com", "password", "takenalias");
        when(loadUserPort.findByEmail("test@test.com")).thenReturn(Optional.empty());
        when(accountsPort.existsByAlias("takenalias")).thenReturn(true);

        assertThrows(AratiriException.class, () -> adapter.initiateRegistration(command));
        verify(registrationDraftPort, never()).save(any());
    }

    @Test
    void initiateRegistration_succeedsWithBlankAlias() {
        RegistrationCommand command = new RegistrationCommand("Test User", "test@test.com", "password", "   ");
        when(loadUserPort.findByEmail("test@test.com")).thenReturn(Optional.empty());
        when(passwordEncoderPort.encode("password")).thenReturn("encoded");

        adapter.initiateRegistration(command);

        verify(registrationDraftPort).save(any(RegistrationDraft.class));
    }

    @Test
    void completeRegistration_createsUserAndAccount() {
        RegistrationDraft draft = new RegistrationDraft("test@test.com", "Test User", "encodedPass",
                "alias", "123456", LocalDateTime.of(2025, 1, 1, 12, 30));
        AuthUser user = new AuthUser("user-1", "Test User", "test@test.com", AuthProvider.LOCAL, Role.USER);
        RefreshToken refreshToken = new RefreshToken("refresh-token", "user-1", Instant.parse("2025-01-01T13:00:00Z"));

        when(registrationDraftPort.findByEmail("test@test.com")).thenReturn(Optional.of(draft));
        when(userCommandPort.registerLocalUser("Test User", "test@test.com", "encodedPass")).thenReturn(user);
        when(accessTokenPort.generateAccessToken("test@test.com")).thenReturn("access-token");
        when(refreshTokenPort.createRefreshToken("user-1")).thenReturn(refreshToken);

        AuthTokens result = adapter.completeRegistration(
                new VerificationCommand("test@test.com", "123456"));

        assertEquals("access-token", result.accessToken());
        assertEquals("refresh-token", result.refreshToken());
        verify(accountsPort).createAccount(any(), eq("user-1"));
        verify(registrationDraftPort).deleteByEmail("test@test.com");
    }

    @Test
    void completeRegistration_throwsWhenDraftNotFound() {
        when(registrationDraftPort.findByEmail("test@test.com")).thenReturn(Optional.empty());
        VerificationCommand command = new VerificationCommand("test@test.com", "123456");

        assertThrows(AratiriException.class, () -> adapter.completeRegistration(command));
    }

    @Test
    void completeRegistration_throwsWhenCodeExpired() {
        RegistrationDraft draft = new RegistrationDraft("test@test.com", "Test User", "encoded",
                null, "123456", LocalDateTime.of(2025, 1, 1, 11, 59));
        when(registrationDraftPort.findByEmail("test@test.com")).thenReturn(Optional.of(draft));
        VerificationCommand command = new VerificationCommand("test@test.com", "123456");

        assertThrows(AratiriException.class, () -> adapter.completeRegistration(command));
        verify(registrationDraftPort).deleteByEmail("test@test.com");
    }

    @Test
    void completeRegistration_throwsWhenCodeMismatch() {
        RegistrationDraft draft = new RegistrationDraft("test@test.com", "Test User", "encoded",
                null, "654321", LocalDateTime.of(2025, 1, 1, 12, 30));
        when(registrationDraftPort.findByEmail("test@test.com")).thenReturn(Optional.of(draft));
        VerificationCommand command = new VerificationCommand("test@test.com", "123456");

        assertThrows(AratiriException.class, () -> adapter.completeRegistration(command));
        verify(userCommandPort, never()).registerLocalUser(anyString(), anyString(), anyString());
    }
}
