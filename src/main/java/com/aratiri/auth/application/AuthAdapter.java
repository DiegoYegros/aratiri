package com.aratiri.auth.application;

import com.aratiri.auth.application.port.in.AuthPort;
import com.aratiri.auth.application.port.out.*;
import com.aratiri.auth.domain.AuthProvider;
import com.aratiri.auth.domain.AuthTokens;
import com.aratiri.auth.domain.AuthUser;
import com.aratiri.errors.ApplicationException;
import org.springframework.http.HttpStatus;
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
                .orElseThrow(() -> new ApplicationException("User not authenticated", HttpStatus.UNAUTHORIZED.value()));
        return loadUserPort.findByEmail(email)
                .orElseThrow(() -> new ApplicationException("User not found", HttpStatus.NOT_FOUND.value()));
    }

    @Override
    public AuthTokens login(String username, String password) {
        AuthUser user = loadUserPort.findByEmail(username)
                .orElseThrow(() -> new ApplicationException("Invalid username or password", HttpStatus.UNAUTHORIZED.value()));
        if (user.provider() != AuthProvider.LOCAL) {
            throw new ApplicationException("Please log in using your federated identity provider.", HttpStatus.BAD_REQUEST.value());
        }
        authenticationPort.authenticate(username, password);
        String accessToken = accessTokenPort.generateAccessToken(username);
        String refreshToken = refreshTokenPort.createRefreshToken(user.id()).token();
        return new AuthTokens(accessToken, refreshToken);
    }

    @Override
    public void logout(String refreshToken) {
        refreshTokenPort.deleteRefreshToken(refreshToken);
    }
}
