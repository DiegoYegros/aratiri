package com.aratiri.auth.application.port.out;

public interface RefreshTokenPort {

    String createRefreshToken(String userId);

    void deleteRefreshToken(String refreshToken);
}
