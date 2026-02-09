package com.aratiri.auth.application;

import com.aratiri.auth.application.port.out.*;
import com.aratiri.auth.domain.AuthProvider;
import com.aratiri.auth.domain.AuthTokens;
import com.aratiri.auth.domain.AuthUser;
import com.aratiri.auth.domain.RefreshToken;
import com.aratiri.auth.domain.Role;
import com.aratiri.shared.exception.AratiriException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthAdapterTest {

    @Mock
    private AuthenticationPort authenticationPort;

    @Mock
    private AuthenticatedUserPort authenticatedUserPort;

    @Mock
    private LoadUserPort loadUserPort;

    @Mock
    private AccessTokenPort accessTokenPort;

    @Mock
    private RefreshTokenPort refreshTokenPort;

    private AuthAdapter authAdapter;

    @BeforeEach
    void setUp() {
        authAdapter = new AuthAdapter(authenticationPort, authenticatedUserPort, loadUserPort, accessTokenPort, refreshTokenPort);
    }

    @Test
    void login_shouldReturnTokensForLocalUser() {
        AuthUser user = new AuthUser("user-1", "Test User", "test@example.com", AuthProvider.LOCAL, Role.USER);
        when(loadUserPort.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(accessTokenPort.generateAccessToken("test@example.com")).thenReturn("access-token");
        when(refreshTokenPort.createRefreshToken("user-1")).thenReturn(new RefreshToken("refresh-token", "user-1", Instant.now().plusSeconds(3600)));

        AuthTokens tokens = authAdapter.login("test@example.com", "password");

        assertEquals("access-token", tokens.accessToken());
        assertEquals("refresh-token", tokens.refreshToken());
        verify(authenticationPort).authenticate("test@example.com", "password");
    }

    @Test
    void login_shouldRejectNonLocalUsers() {
        AuthUser user = new AuthUser("user-1", "Test User", "test@example.com", AuthProvider.GOOGLE, Role.USER);
        when(loadUserPort.findByEmail("test@example.com")).thenReturn(Optional.of(user));

        AratiriException ex = assertThrows(AratiriException.class,
                () -> authAdapter.login("test@example.com", "password"));

        assertTrue(ex.getMessage().contains("federated"));
    }

    @Test
    void getCurrentUser_shouldReturnAuthUser() {
        AuthUser user = new AuthUser("user-1", "Test User", "test@example.com", AuthProvider.LOCAL, Role.USER);
        when(authenticatedUserPort.getCurrentUserEmail()).thenReturn(Optional.of("test@example.com"));
        when(loadUserPort.findByEmail("test@example.com")).thenReturn(Optional.of(user));

        AuthUser result = authAdapter.getCurrentUser();

        assertEquals("user-1", result.id());
    }

    @Test
    void getCurrentUser_shouldThrowWhenNotAuthenticated() {
        when(authenticatedUserPort.getCurrentUserEmail()).thenReturn(Optional.empty());

        assertThrows(AratiriException.class, () -> authAdapter.getCurrentUser());
    }

    @Test
    void logout_shouldDeleteRefreshToken() {
        authAdapter.logout("refresh-token");

        verify(refreshTokenPort).deleteRefreshToken("refresh-token");
    }
}
