package com.aratiri.service.impl;

import com.aratiri.config.AratiriProperties;
import com.aratiri.entity.RefreshTokenEntity;
import com.aratiri.entity.UserEntity;
import com.aratiri.core.exception.AratiriException;
import com.aratiri.repository.RefreshTokenRepository;
import com.aratiri.repository.UserRepository;
import com.aratiri.service.RefreshTokenService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final AratiriProperties properties;

    public RefreshTokenServiceImpl(RefreshTokenRepository refreshTokenRepository, UserRepository userRepository, AratiriProperties properties) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.properties = properties;
    }

    public Optional<RefreshTokenEntity> findByToken(String token) {
        return refreshTokenRepository.findByToken(token);
    }

    @Override
    public void deleteRefreshToken(String refreshToken) {
        refreshTokenRepository.findByToken(refreshToken).ifPresent(refreshTokenRepository::delete);
    }

    @Transactional
    public RefreshTokenEntity createRefreshToken(String userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new AratiriException("User not found", HttpStatus.NOT_FOUND));
        return refreshTokenRepository.findByUser(user)
                .map(existingToken -> {
                    existingToken.setExpiryDate(Instant.now().plusSeconds(properties.getJwtRefreshExpiration()));
                    existingToken.setToken(UUID.randomUUID().toString());
                    return refreshTokenRepository.save(existingToken);
                })
                .orElseGet(() -> {
                    RefreshTokenEntity newRefreshToken = new RefreshTokenEntity();
                    newRefreshToken.setUser(user);
                    newRefreshToken.setExpiryDate(Instant.now().plusSeconds(properties.getJwtRefreshExpiration()));
                    newRefreshToken.setToken(UUID.randomUUID().toString());
                    return refreshTokenRepository.save(newRefreshToken);
                });
    }

    public RefreshTokenEntity verifyExpiration(RefreshTokenEntity token) {
        if (token.getExpiryDate().compareTo(Instant.now()) < 0) {
            refreshTokenRepository.delete(token);
            throw new AratiriException("Refresh token was expired. Please make a new sign-in request", HttpStatus.BAD_REQUEST);
        }
        return token;
    }
}
