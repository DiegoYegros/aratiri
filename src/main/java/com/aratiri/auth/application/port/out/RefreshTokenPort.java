package com.aratiri.auth.application.port.out;

import com.aratiri.auth.domain.RefreshToken;

import java.util.Optional;

public interface RefreshTokenPort {

    RefreshToken createRefreshToken(String userId);

    Optional<RefreshToken> findByToken(String token);

    void deleteRefreshToken(String refreshToken);

    /**
     * Invalidate {@code presentedToken} and issue a new refresh token in one atomic
     * update. Empty when the presented token is missing or was already rotated away
     * (concurrent refresh / reuse).
     */
    Optional<RefreshToken> rotateRefreshToken(String presentedToken);
}
