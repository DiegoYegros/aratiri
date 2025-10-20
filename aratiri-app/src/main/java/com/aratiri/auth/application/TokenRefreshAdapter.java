package com.aratiri.auth.application;

import com.aratiri.auth.application.port.in.TokenRefreshPort;
import com.aratiri.auth.application.port.out.AccessTokenPort;
import com.aratiri.auth.application.port.out.LoadUserPort;
import com.aratiri.auth.application.port.out.RefreshTokenPort;
import com.aratiri.auth.domain.AuthTokens;
import com.aratiri.auth.domain.AuthUser;
import com.aratiri.auth.domain.RefreshToken;
import com.aratiri.shared.exception.AratiriException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

@Service
public class TokenRefreshAdapter implements TokenRefreshPort {

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
                .orElseThrow(() -> new AratiriException("Refresh token is not in database!", HttpStatus.BAD_REQUEST.value()));
        Instant now = Instant.now(clock);
        if (refreshToken.isExpired(now)) {
            refreshTokenPort.deleteRefreshToken(refreshTokenValue);
            throw new AratiriException("Refresh token was expired. Please make a new sign-in request", HttpStatus.BAD_REQUEST.value());
        }
        AuthUser user = loadUserPort.findById(refreshToken.userId())
                .orElseThrow(() -> new AratiriException("User not found", HttpStatus.NOT_FOUND.value()));
        String accessToken = accessTokenPort.generateAccessToken(user.email());
        return new AuthTokens(accessToken, refreshTokenValue);
    }
}
