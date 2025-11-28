package com.aratiri.auth.application.port.out;

import com.aratiri.auth.domain.PasswordResetToken;

import java.util.Optional;

public interface PasswordResetTokenPort {

    void save(PasswordResetToken token);

    Optional<PasswordResetToken> findByUserId(String userId);

    void deleteByUserId(String userId);
}
