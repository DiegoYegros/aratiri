package com.aratiri.auth.application;

import com.aratiri.auth.application.port.in.TokenExchangePort;
import com.aratiri.auth.application.port.out.AccessTokenPort;
import com.aratiri.auth.application.port.out.RefreshTokenPort;
import com.aratiri.auth.domain.AuthTokens;
import com.aratiri.auth.domain.AuthUser;
import com.aratiri.auth.infrastructure.security.AratiriJwtPrincipalService;
import com.aratiri.infrastructure.configuration.security.AratiriSecurityProperties;
import com.aratiri.shared.exception.AratiriException;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

@Service
public class TokenExchangeAdapter implements TokenExchangePort {

    private final JwtDecoder jwtDecoder;
    private final AratiriJwtPrincipalService principalService;
    private final AccessTokenPort accessTokenPort;
    private final RefreshTokenPort refreshTokenPort;
    private final AratiriSecurityProperties securityProperties;

    public TokenExchangeAdapter(
            JwtDecoder jwtDecoder,
            AratiriJwtPrincipalService principalService,
            AccessTokenPort accessTokenPort,
            RefreshTokenPort refreshTokenPort,
            AratiriSecurityProperties securityProperties
    ) {
        this.jwtDecoder = jwtDecoder;
        this.principalService = principalService;
        this.accessTokenPort = accessTokenPort;
        this.refreshTokenPort = refreshTokenPort;
        this.securityProperties = securityProperties;
    }

    @Override
    public AuthTokens exchange(String externalToken) {
        if (!securityProperties.getTokenExchange().isEnabled()) {
            throw new AratiriException("Token exchange is disabled", HttpStatus.NOT_FOUND.value());
        }

        try {
            Jwt jwt = jwtDecoder.decode(externalToken);
            AuthUser user = principalService.resolveUser(jwt);
            String accessToken = accessTokenPort.generateAccessToken(user.email());
            String refreshToken = refreshTokenPort.createRefreshToken(user.id()).token();
            return new AuthTokens(accessToken, refreshToken);
        } catch (JwtException ex) {
            throw new AratiriException("External token validation failed", HttpStatus.UNAUTHORIZED.value());
        }
    }
}
