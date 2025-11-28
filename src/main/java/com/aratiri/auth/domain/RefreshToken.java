package com.aratiri.auth.domain;

import java.time.Instant;

public record RefreshToken(String token, String userId, Instant expiresAt) {
    public boolean isExpired(Instant now) {
        return expiresAt.isBefore(now);
    }
}
