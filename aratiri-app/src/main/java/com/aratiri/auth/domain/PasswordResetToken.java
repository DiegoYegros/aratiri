package com.aratiri.auth.domain;

import java.time.Clock;
import java.time.LocalDateTime;

public record PasswordResetToken(
        String userId,
        String code,
        LocalDateTime expiresAt
) {
    public boolean isExpired(Clock clock) {
        return expiresAt.isBefore(LocalDateTime.now(clock));
    }
}
