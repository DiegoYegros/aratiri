package com.aratiri.auth.infrastructure.persistence;

import com.aratiri.auth.application.port.out.PasswordResetTokenPort;
import com.aratiri.auth.domain.PasswordResetToken;
import com.aratiri.infrastructure.persistence.jpa.entity.PasswordResetData;
import com.aratiri.infrastructure.persistence.jpa.entity.UserEntity;
import com.aratiri.infrastructure.persistence.jpa.repository.PasswordResetDataRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class PasswordResetDataRepositoryAdapter implements PasswordResetTokenPort {

    private final PasswordResetDataRepository passwordResetDataRepository;
    private final UserRepository userRepository;

    public PasswordResetDataRepositoryAdapter(
            PasswordResetDataRepository passwordResetDataRepository,
            UserRepository userRepository
    ) {
        this.passwordResetDataRepository = passwordResetDataRepository;
        this.userRepository = userRepository;
    }

    @Override
    public void save(PasswordResetToken token) {
        UserEntity user = userRepository.findById(token.userId()).orElse(null);
        if (user == null) {
            return;
        }
        PasswordResetData resetData = passwordResetDataRepository.findByUser(user)
                .orElseGet(PasswordResetData::new);
        resetData.setUser(user);
        resetData.setCode(token.code());
        resetData.setExpiryDate(token.expiresAt());
        passwordResetDataRepository.save(resetData);
    }

    @Override
    public Optional<PasswordResetToken> findByUserId(String userId) {
        return userRepository.findById(userId)
                .flatMap(passwordResetDataRepository::findByUser)
                .map(resetData -> new PasswordResetToken(
                        resetData.getUser().getId(),
                        resetData.getCode(),
                        resetData.getExpiryDate()
                ));
    }

    @Override
    public void deleteByUserId(String userId) {
        userRepository.findById(userId)
                .flatMap(passwordResetDataRepository::findByUser)
                .ifPresent(passwordResetDataRepository::delete);
    }
}
