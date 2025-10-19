package com.aratiri.service;

import com.aratiri.entity.RefreshTokenEntity;

import java.util.Optional;

public interface RefreshTokenService {
    RefreshTokenEntity createRefreshToken(String userId);

    RefreshTokenEntity verifyExpiration(RefreshTokenEntity token);

    Optional<RefreshTokenEntity> findByToken(String token);

    void deleteRefreshToken(String refreshToken);
}
