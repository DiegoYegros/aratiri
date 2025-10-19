package com.aratiri.auth.application.port.in;

import com.aratiri.auth.domain.AuthTokens;

public interface TokenRefreshPort {

    AuthTokens refreshAccessToken(String refreshToken);
}
