package com.aratiri.auth.application.port.in;

import com.aratiri.auth.domain.AuthTokens;
import com.aratiri.auth.domain.AuthUser;

public interface AuthPort {

    AuthUser getCurrentUser();

    AuthTokens login(String username, String password);

    void logout(String refreshToken);
}
