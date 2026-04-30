package com.aratiri.auth.application;

import com.aratiri.accounts.application.dto.AccountDTO;
import com.aratiri.accounts.application.dto.CreateAccountRequestDTO;
import com.aratiri.accounts.application.port.in.AccountsPort;
import com.aratiri.auth.application.port.out.*;
import com.aratiri.auth.domain.*;
import com.aratiri.shared.exception.AratiriException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoogleAuthAdapterTest {

    @Mock
    private GoogleTokenVerifierPort googleTokenVerifierPort;

    @Mock
    private LoadUserPort loadUserPort;

    @Mock
    private UserCommandPort userCommandPort;

    @Mock
    private AccountsPort accountsPort;

    @Mock
    private AccessTokenPort accessTokenPort;

    @Mock
    private RefreshTokenPort refreshTokenPort;

    private GoogleAuthAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new GoogleAuthAdapter(
                googleTokenVerifierPort,
                loadUserPort,
                userCommandPort,
                accountsPort,
                accessTokenPort,
                refreshTokenPort
        );
    }

    @Test
    void loginWithGoogle_returnsTokensForExistingGoogleUser() {
        GoogleUserProfile profile = new GoogleUserProfile("user@test.com", "Test User");
        AuthUser existingUser = new AuthUser("user-1", "Test User", "user@test.com", AuthProvider.GOOGLE, Role.USER);
        RefreshToken refreshToken = new RefreshToken("refresh-token", "user-1", Instant.now().plusSeconds(3600));

        when(googleTokenVerifierPort.verify("google-token")).thenReturn(profile);
        when(loadUserPort.findByEmail("user@test.com")).thenReturn(Optional.of(existingUser));
        when(accessTokenPort.generateAccessToken("user@test.com")).thenReturn("access-token");
        when(refreshTokenPort.createRefreshToken("user-1")).thenReturn(refreshToken);

        AuthTokens result = adapter.loginWithGoogle("google-token");

        assertEquals("access-token", result.accessToken());
        assertEquals("refresh-token", result.refreshToken());
        verify(accountsPort, never()).createAccount(any(), anyString());
    }

    @Test
    void loginWithGoogle_createsNewUserAndReturnsTokens() {
        GoogleUserProfile profile = new GoogleUserProfile("new@test.com", "New User");
        AuthUser newUser = new AuthUser("user-2", "New User", "new@test.com", AuthProvider.GOOGLE, Role.USER);
        RefreshToken refreshToken = new RefreshToken("refresh-token", "user-2", Instant.now().plusSeconds(3600));

        when(googleTokenVerifierPort.verify("google-token")).thenReturn(profile);
        when(loadUserPort.findByEmail("new@test.com")).thenReturn(Optional.empty());
        when(userCommandPort.registerSocialUser("New User", "new@test.com", AuthProvider.GOOGLE, Role.USER))
                .thenReturn(newUser);
        when(accountsPort.createAccount(any(CreateAccountRequestDTO.class), eq("user-2")))
                .thenReturn(AccountDTO.builder().id("acc-1").userId("user-2").build());
        when(accessTokenPort.generateAccessToken("new@test.com")).thenReturn("access-token");
        when(refreshTokenPort.createRefreshToken("user-2")).thenReturn(refreshToken);

        AuthTokens result = adapter.loginWithGoogle("google-token");

        assertEquals("access-token", result.accessToken());
        assertEquals("refresh-token", result.refreshToken());
        verify(accountsPort).createAccount(any(CreateAccountRequestDTO.class), eq("user-2"));
    }

    @Test
    void loginWithGoogle_throwsWhenProviderMismatch() {
        GoogleUserProfile profile = new GoogleUserProfile("user@test.com", "Test User");
        AuthUser localUser = new AuthUser("user-1", "Test User", "user@test.com", AuthProvider.LOCAL, Role.USER);

        when(googleTokenVerifierPort.verify("google-token")).thenReturn(profile);
        when(loadUserPort.findByEmail("user@test.com")).thenReturn(Optional.of(localUser));

        assertThrows(AratiriException.class, () -> adapter.loginWithGoogle("google-token"));
    }

    @Test
    void loginWithGoogle_wrapsUnknownException() {
        when(googleTokenVerifierPort.verify("google-token"))
                .thenThrow(new RuntimeException("Network error"));

        assertThrows(AratiriException.class, () -> adapter.loginWithGoogle("google-token"));
    }
}
