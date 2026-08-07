package com.aratiri.auth.application;

import com.aratiri.auth.application.port.out.AccessTokenPort;
import com.aratiri.auth.application.port.out.LoadUserPort;
import com.aratiri.auth.application.port.out.RefreshTokenPort;
import com.aratiri.auth.domain.AuthProvider;
import com.aratiri.auth.domain.AuthTokens;
import com.aratiri.auth.domain.AuthUser;
import com.aratiri.auth.domain.RefreshToken;
import com.aratiri.auth.domain.Role;
import com.aratiri.errors.ApplicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenRefreshAdapterTest {

    @Mock
    private RefreshTokenPort refreshTokenPort;

    @Mock
    private AccessTokenPort accessTokenPort;

    @Mock
    private LoadUserPort loadUserPort;

    private Clock clock = Clock.fixed(Instant.parse("2025-01-01T00:00:00Z"), ZoneId.of("UTC"));

    private TokenRefreshAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new TokenRefreshAdapter(refreshTokenPort, accessTokenPort, loadUserPort, clock);
    }

    @Test
    void refreshAccessToken_returnsTokensWhenValid() {
        RefreshToken refreshToken = new RefreshToken("refresh-token", "user-1", Instant.parse("2025-01-01T01:00:00Z"));
        RefreshToken rotated = new RefreshToken("new-refresh-token", "user-1", Instant.parse("2025-01-02T00:00:00Z"));
        AuthUser user = new AuthUser("user-1", "Test", "test@test.com", AuthProvider.LOCAL, Role.USER);
        when(refreshTokenPort.findByToken("refresh-token")).thenReturn(Optional.of(refreshToken));
        when(refreshTokenPort.rotateRefreshToken("refresh-token")).thenReturn(Optional.of(rotated));
        when(loadUserPort.findById("user-1")).thenReturn(Optional.of(user));
        when(accessTokenPort.generateAccessToken("test@test.com")).thenReturn("new-access-token");

        AuthTokens result = adapter.refreshAccessToken("refresh-token");

        assertEquals("new-access-token", result.accessToken());
        assertEquals("new-refresh-token", result.refreshToken());
        assertNotEquals("refresh-token", result.refreshToken());
    }

    @Test
    void refreshAccessToken_throwsWhenTokenNotFound() {
        when(refreshTokenPort.findByToken("unknown")).thenReturn(Optional.empty());

        ApplicationException ex = assertThrows(ApplicationException.class, () -> adapter.refreshAccessToken("unknown"));
        assertEquals(HttpStatus.BAD_REQUEST.value(), ex.getStatus());
    }

    @Test
    void refreshAccessToken_throwsWhenRotateLosesRace() {
        RefreshToken refreshToken = new RefreshToken("refresh-token", "user-1", Instant.parse("2025-01-01T01:00:00Z"));
        when(refreshTokenPort.findByToken("refresh-token")).thenReturn(Optional.of(refreshToken));
        when(refreshTokenPort.rotateRefreshToken("refresh-token")).thenReturn(Optional.empty());

        ApplicationException ex = assertThrows(ApplicationException.class, () -> adapter.refreshAccessToken("refresh-token"));
        assertEquals(HttpStatus.BAD_REQUEST.value(), ex.getStatus());
    }

    @Test
    void refreshAccessToken_throwsWhenTokenExpired() {
        RefreshToken refreshToken = new RefreshToken("expired-token", "user-1", Instant.parse("2024-12-31T23:59:59Z"));
        when(refreshTokenPort.findByToken("expired-token")).thenReturn(Optional.of(refreshToken));

        ApplicationException ex = assertThrows(ApplicationException.class, () -> adapter.refreshAccessToken("expired-token"));
        assertEquals(HttpStatus.BAD_REQUEST.value(), ex.getStatus());
        verify(refreshTokenPort).deleteRefreshToken("expired-token");
    }
}
