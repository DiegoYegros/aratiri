package com.aratiri.auth.application;

import com.aratiri.accounts.application.port.in.AccountsPort;
import com.aratiri.auth.application.port.in.GoogleAuthPort;
import com.aratiri.auth.application.port.out.AccessTokenPort;
import com.aratiri.auth.application.port.out.GoogleTokenVerifierPort;
import com.aratiri.auth.application.port.out.LoadUserPort;
import com.aratiri.auth.application.port.out.RefreshTokenPort;
import com.aratiri.auth.application.port.out.UserCommandPort;
import com.aratiri.auth.domain.AuthTokens;
import com.aratiri.auth.domain.AuthUser;
import com.aratiri.auth.domain.GoogleUserProfile;
import com.aratiri.core.exception.AratiriException;
import com.aratiri.dto.accounts.CreateAccountRequestDTO;
import com.aratiri.enums.AuthProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class GoogleAuthAdapter implements GoogleAuthPort {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final GoogleTokenVerifierPort googleTokenVerifierPort;
    private final LoadUserPort loadUserPort;
    private final UserCommandPort userCommandPort;
    private final AccountsPort accountsPort;
    private final AccessTokenPort accessTokenPort;
    private final RefreshTokenPort refreshTokenPort;

    public GoogleAuthAdapter(
            GoogleTokenVerifierPort googleTokenVerifierPort,
            LoadUserPort loadUserPort,
            UserCommandPort userCommandPort,
            AccountsPort accountsPort,
            AccessTokenPort accessTokenPort,
            RefreshTokenPort refreshTokenPort
    ) {
        this.googleTokenVerifierPort = googleTokenVerifierPort;
        this.loadUserPort = loadUserPort;
        this.userCommandPort = userCommandPort;
        this.accountsPort = accountsPort;
        this.accessTokenPort = accessTokenPort;
        this.refreshTokenPort = refreshTokenPort;
    }

    @Override
    public AuthTokens loginWithGoogle(String googleToken) {
        try {
            GoogleUserProfile profile = googleTokenVerifierPort.verify(googleToken);
            AuthUser user = loadUserPort.findByEmail(profile.email())
                    .map(existing -> {
                        if (existing.provider() != AuthProvider.GOOGLE) {
                            throw new AratiriException("This email is registered with a password. Please use the standard login.", HttpStatus.BAD_REQUEST);
                        }
                        return existing;
                    })
                    .orElseGet(() -> {
                        logger.info("Creating new user for email via Google SSO: {}", profile.email());
                        AuthUser newUser = userCommandPort.registerSocialUser(profile.name(), profile.email(), AuthProvider.GOOGLE);
                        CreateAccountRequestDTO createAccountRequest = new CreateAccountRequestDTO();
                        createAccountRequest.setUserId(newUser.id());
                        accountsPort.createAccount(createAccountRequest, newUser.id());
                        return newUser;
                    });
            String accessToken = accessTokenPort.generateAccessToken(user.email());
            String refreshToken = refreshTokenPort.createRefreshToken(user.id()).token();
            return new AuthTokens(accessToken, refreshToken);
        } catch (AratiriException ex) {
            throw ex;
        } catch (Exception ex) {
            logger.error("Auth failed with Google, message is: {}", ex.getMessage(), ex);
            throw new AratiriException("Auth Failed with Google", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
