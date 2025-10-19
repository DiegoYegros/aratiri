package com.aratiri.auth.infrastructure.tokens;

import com.aratiri.auth.application.port.out.RefreshTokenPort;
import com.aratiri.service.RefreshTokenService;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenServiceAdapter implements RefreshTokenPort {

    private final RefreshTokenService refreshTokenService;

    public RefreshTokenServiceAdapter(RefreshTokenService refreshTokenService) {
        this.refreshTokenService = refreshTokenService;
    }

    @Override
    public String createRefreshToken(String userId) {
        return refreshTokenService.createRefreshToken(userId).getToken();
    }

    @Override
    public void deleteRefreshToken(String refreshToken) {
        refreshTokenService.deleteRefreshToken(refreshToken);
    }
}
