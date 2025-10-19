package com.aratiri.auth.domain;

import java.time.Clock;
import java.time.LocalDateTime;

public record RegistrationDraft(
        String email,
        String name,
        String encodedPassword,
        String alias,
        String code,
        LocalDateTime expiresAt
) {
    public boolean isExpired(Clock clock) {
        return expiresAt.isBefore(LocalDateTime.now(clock));
    }
}
