package com.aratiri.auth.infrastructure.tokens;

import com.aratiri.auth.application.port.out.RefreshTokenPort;
import com.aratiri.auth.domain.RefreshToken;
import com.aratiri.config.AratiriProperties;
import com.aratiri.core.exception.AratiriException;
import com.aratiri.entity.RefreshTokenEntity;
import com.aratiri.entity.UserEntity;
import com.aratiri.repository.RefreshTokenRepository;
import com.aratiri.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
public class RefreshTokenRepositoryAdapter implements RefreshTokenPort {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final AratiriProperties properties;

    public RefreshTokenRepositoryAdapter(
            RefreshTokenRepository refreshTokenRepository,
            UserRepository userRepository,
            AratiriProperties properties
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.properties = properties;
    }

    @Override
    public RefreshToken createRefreshToken(String userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new AratiriException("User not found", HttpStatus.NOT_FOUND));
        Instant expiry = Instant.now().plusSeconds(properties.getJwtRefreshExpiration());
        RefreshTokenEntity entity = refreshTokenRepository.findByUser(user)
                .map(existing -> {
                    existing.setToken(UUID.randomUUID().toString());
                    existing.setExpiryDate(expiry);
                    return existing;
                })
                .orElseGet(() -> {
                    RefreshTokenEntity newEntity = new RefreshTokenEntity();
                    newEntity.setUser(user);
                    newEntity.setToken(UUID.randomUUID().toString());
                    newEntity.setExpiryDate(expiry);
                    return newEntity;
                });
        RefreshTokenEntity saved = refreshTokenRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<RefreshToken> findByToken(String token) {
        return refreshTokenRepository.findByToken(token).map(this::toDomain);
    }

    @Override
    public void deleteRefreshToken(String refreshToken) {
        refreshTokenRepository.findByToken(refreshToken).ifPresent(refreshTokenRepository::delete);
    }

    private RefreshToken toDomain(RefreshTokenEntity entity) {
        return new RefreshToken(entity.getToken(), entity.getUser().getId(), entity.getExpiryDate());
    }
}
