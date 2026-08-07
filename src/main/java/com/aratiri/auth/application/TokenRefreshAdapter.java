package com.aratiri.auth.application;

import com.aratiri.auth.application.port.in.TokenRefreshPort;
import com.aratiri.auth.application.port.out.AccessTokenPort;
import com.aratiri.auth.application.port.out.LoadUserPort;
import com.aratiri.auth.application.port.out.RefreshTokenPort;
import com.aratiri.auth.domain.AuthTokens;
import com.aratiri.auth.domain.AuthUser;
import com.aratiri.auth.domain.RefreshToken;
import com.aratiri.errors.ApplicationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

@Service
public class TokenRefreshAdapter implements TokenRefreshPort {

    /** Stable client-facing copy for missing, expired, and rotate-race refresh failures. */
    public static final String INVALID_REFRESH_MESSAGE =
            "Invalid or expired refresh token. Please sign in again.";

    private final RefreshTokenPort refreshTokenPort;
    private final AccessTokenPort accessTokenPort;
    private final LoadUserPort loadUserPort;
    private final Clock clock;

    public TokenRefreshAdapter(
            RefreshTokenPort refreshTokenPort,
            AccessTokenPort accessTokenPort,
            LoadUserPort loadUserPort,
            Clock clock
    ) {
        this.refreshTokenPort = refreshTokenPort;
        this.accessTokenPort = accessTokenPort;
        this.loadUserPort = loadUserPort;
        this.clock = clock;
    }

    @Override
    public AuthTokens refreshAccessToken(String refreshTokenValue) {
        RefreshToken refreshToken = refreshTokenPort.findByToken(refreshTokenValue)
                .orElseThrow(() -> invalidRefresh());
        Instant now = Instant.now(clock);
        if (refreshToken.isExpired(now)) {
            refreshTokenPort.deleteRefreshToken(refreshTokenValue);
            throw invalidRefresh();
        }
        RefreshToken rotated = refreshTokenPort.rotateRefreshToken(refreshTokenValue)
                .orElseThrow(() -> invalidRefresh());
        AuthUser user = loadUserPort.findById(rotated.userId())
                .orElseThrow(() -> new ApplicationException("User not found", HttpStatus.NOT_FOUND.value()));
        String accessToken = accessTokenPort.generateAccessToken(user.email());
        return new AuthTokens(accessToken, rotated.token());
    }

    private static ApplicationException invalidRefresh() {
        return new ApplicationException(INVALID_REFRESH_MESSAGE, HttpStatus.BAD_REQUEST.value());
    }
}
