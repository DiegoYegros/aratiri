package com.aratiri.auth.application.service;

import com.aratiri.auth.application.port.in.AuthPort;
import com.aratiri.auth.application.port.out.AccessTokenPort;
import com.aratiri.auth.application.port.out.AuthenticatedUserPort;
import com.aratiri.auth.application.port.out.AuthenticationPort;
import com.aratiri.auth.application.port.out.LoadUserPort;
import com.aratiri.auth.application.port.out.RefreshTokenPort;
import com.aratiri.auth.domain.AuthTokens;
import com.aratiri.auth.domain.AuthUser;
import com.aratiri.enums.AuthProvider;
import com.aratiri.core.exception.AratiriException;
import org.springframework.stereotype.Service;

@Service
public class AuthAdapter implements AuthPort {

    private final AuthenticationPort authenticationPort;
    private final AuthenticatedUserPort authenticatedUserPort;
    private final LoadUserPort loadUserPort;
    private final AccessTokenPort accessTokenPort;
    private final RefreshTokenPort refreshTokenPort;

    public AuthAdapter(
            AuthenticationPort authenticationPort,
            AuthenticatedUserPort authenticatedUserPort,
            LoadUserPort loadUserPort,
            AccessTokenPort accessTokenPort,
            RefreshTokenPort refreshTokenPort) {
        this.authenticationPort = authenticationPort;
        this.authenticatedUserPort = authenticatedUserPort;
        this.loadUserPort = loadUserPort;
        this.accessTokenPort = accessTokenPort;
        this.refreshTokenPort = refreshTokenPort;
    }

    @Override
    public AuthUser getCurrentUser() {
        String email = authenticatedUserPort.getCurrentUserEmail()
                .orElseThrow(() -> new AratiriException("User not authenticated"));
        return loadUserPort.findByEmail(email)
                .orElseThrow(() -> new AratiriException("User not found"));
    }

    @Override
    public AuthTokens login(String username, String password) {
        AuthUser user = loadUserPort.findByEmail(username)
                .orElseThrow(() -> new AratiriException("Invalid username or password"));
        if (user.provider() == AuthProvider.GOOGLE) {
            throw new AratiriException("Please log in using your Google account.");
        }
        authenticationPort.authenticate(username, password);
        String accessToken = accessTokenPort.generateAccessToken(username);
        String refreshToken = refreshTokenPort.createRefreshToken(user.id());
        return new AuthTokens(accessToken, refreshToken);
    }

    @Override
    public void logout(String refreshToken) {
        refreshTokenPort.deleteRefreshToken(refreshToken);
    }
}
