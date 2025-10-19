package com.aratiri.auth.application.port.out;

import com.aratiri.auth.domain.RefreshToken;

import java.util.Optional;

public interface RefreshTokenPort {

    RefreshToken createRefreshToken(String userId);

    Optional<RefreshToken> findByToken(String token);

    void deleteRefreshToken(String refreshToken);
}
