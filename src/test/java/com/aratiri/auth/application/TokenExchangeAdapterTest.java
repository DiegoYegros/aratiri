package com.aratiri.auth.application;

import com.aratiri.auth.application.port.out.AccessTokenPort;
import com.aratiri.auth.application.port.out.RefreshTokenPort;
import com.aratiri.auth.domain.AuthProvider;
import com.aratiri.auth.domain.AuthTokens;
import com.aratiri.auth.domain.AuthUser;
import com.aratiri.auth.domain.RefreshToken;
import com.aratiri.auth.domain.Role;
import com.aratiri.auth.infrastructure.security.AratiriJwtPrincipalService;
import com.aratiri.infrastructure.configuration.security.AratiriSecurityProperties;
import com.aratiri.shared.exception.AratiriException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenExchangeAdapterTest {

    @Mock
    private JwtDecoder jwtDecoder;

    @Mock
    private AratiriJwtPrincipalService principalService;

    @Mock
    private AccessTokenPort accessTokenPort;

    @Mock
    private RefreshTokenPort refreshTokenPort;

    @Mock
    private AratiriSecurityProperties securityProperties;

    private TokenExchangeAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new TokenExchangeAdapter(
                jwtDecoder, principalService, accessTokenPort, refreshTokenPort, securityProperties);
    }

    @Test
    void exchange_returnsTokensWhenEnabled() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("user-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        AuthUser user = new AuthUser("user-1", "Test", "test@test.com", AuthProvider.LOCAL, Role.USER);
        RefreshToken refreshToken = new RefreshToken("refresh-token", "user-1", Instant.now().plusSeconds(3600));

        AratiriSecurityProperties.TokenExchange tokenExProps =
                new AratiriSecurityProperties.TokenExchange();
        tokenExProps.setEnabled(true);
        when(securityProperties.getTokenExchange()).thenReturn(tokenExProps);
        when(jwtDecoder.decode("ext-token")).thenReturn(jwt);
        when(principalService.resolveUser(any())).thenReturn(user);
        when(accessTokenPort.generateAccessToken("test@test.com")).thenReturn("access-token");
        when(refreshTokenPort.createRefreshToken("user-1")).thenReturn(refreshToken);

        AuthTokens result = adapter.exchange("ext-token");

        assertEquals("access-token", result.accessToken());
        assertEquals("refresh-token", result.refreshToken());
    }

    @Test
    void exchange_throwsWhenDisabled() {
        AratiriSecurityProperties.TokenExchange tokenExProps =
                new AratiriSecurityProperties.TokenExchange();
        tokenExProps.setEnabled(false);
        when(securityProperties.getTokenExchange()).thenReturn(tokenExProps);

        assertThrows(AratiriException.class, () -> adapter.exchange("ext-token"));
    }

    @Test
    void exchange_throwsWhenJwtIsInvalid() {
        AratiriSecurityProperties.TokenExchange tokenExProps =
                new AratiriSecurityProperties.TokenExchange();
        tokenExProps.setEnabled(true);
        when(securityProperties.getTokenExchange()).thenReturn(tokenExProps);
        when(jwtDecoder.decode("bad-token")).thenThrow(new JwtException("invalid token"));

        assertThrows(AratiriException.class, () -> adapter.exchange("bad-token"));
    }
}
