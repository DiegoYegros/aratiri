package com.aratiri.auth;

import com.aratiri.auth.application.dto.*;
import com.aratiri.auth.application.port.in.*;
import com.aratiri.auth.application.port.in.PasswordResetPort;
import com.aratiri.auth.application.port.in.RegistrationPort;
import com.aratiri.auth.application.port.in.TokenExchangePort;
import com.aratiri.auth.application.port.in.TokenRefreshPort;
import com.aratiri.auth.domain.AuthProvider;
import com.aratiri.auth.domain.AuthTokens;
import com.aratiri.auth.domain.AuthUser;
import com.aratiri.auth.domain.Role;
import com.aratiri.infrastructure.configuration.security.AratiriSecurityProperties;
import com.aratiri.shared.exception.AratiriException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthAPITest {

    @Mock
    private AuthPort authPort;

    @Mock
    private GoogleAuthPort googleAuthPort;

    @Mock
    private TokenRefreshPort tokenRefreshPort;

    @Mock
    private TokenExchangePort tokenExchangePort;

    @Mock
    private RegistrationPort registrationPort;

    @Mock
    private PasswordResetPort passwordResetPort;

    @Mock
    private AratiriSecurityProperties securityProperties;

    private AuthAPI api;

    @BeforeEach
    void setUp() {
        api = new AuthAPI(authPort, googleAuthPort, tokenRefreshPort, tokenExchangePort,
                registrationPort, passwordResetPort, securityProperties);
    }

    @Test
    void login_returnsTokens() {
        AuthRequestDTO request = new AuthRequestDTO();
        request.setUsername("user");
        request.setPassword("pass");
        AuthTokens tokens = new AuthTokens("access-token", "refresh-token");
        when(authPort.login("user", "pass")).thenReturn(tokens);

        ResponseEntity<AuthResponseDTO> response = api.login(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("access-token", response.getBody().getAccessToken());
    }

    @Test
    void register_returnsCreated() {
        RegistrationRequestDTO request = new RegistrationRequestDTO();
        request.setName("Test");
        request.setEmail("test@test.com");
        request.setPassword("pass");
        request.setAlias("alias");

        ResponseEntity<Void> response = api.register(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    @Test
    void verify_returnsTokens() {
        VerificationRequestDTO request = new VerificationRequestDTO();
        request.setEmail("test@test.com");
        request.setCode("123456");
        AuthTokens tokens = new AuthTokens("access-token", "refresh-token");
        when(registrationPort.completeRegistration(any())).thenReturn(tokens);

        ResponseEntity<AuthResponseDTO> response = api.verify(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("access-token", response.getBody().getAccessToken());
    }

    @Test
    void googleLogin_returnsTokens() {
        AuthTokens tokens = new AuthTokens("access-token", "refresh-token");
        when(googleAuthPort.loginWithGoogle("google-token")).thenReturn(tokens);

        ResponseEntity<AuthResponseDTO> response = api.googleLogin("google-token");

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void refreshToken_returnsTokens() {
        RefreshTokenRequestDTO request = new RefreshTokenRequestDTO();
        request.setRefreshToken("refresh-token");
        AuthTokens tokens = new AuthTokens("new-access", "new-refresh");
        when(tokenRefreshPort.refreshAccessToken("refresh-token")).thenReturn(tokens);

        ResponseEntity<AuthResponseDTO> response = api.refreshToken(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void logout_returnsOk() {
        LogoutRequestDTO request = new LogoutRequestDTO();
        request.setRefreshToken("refresh-token");

        ResponseEntity<Void> response = api.logout(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void me_returnsUser() {
        AuthUser user = new AuthUser("user-1", "Test", "test@test.com", AuthProvider.LOCAL, Role.USER);
        when(authPort.getCurrentUser()).thenReturn(user);

        ResponseEntity<UserDTO> response = api.me();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Test", response.getBody().getName());
    }

    @Test
    void forgotPassword_returnsOk() {
        PasswordResetDTOs.ForgotPasswordRequestDTO request = new PasswordResetDTOs.ForgotPasswordRequestDTO();
        request.setEmail("test@test.com");

        ResponseEntity<Void> response = api.forgotPassword(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void resetPassword_returnsOk() {
        PasswordResetDTOs.ResetPasswordRequestDTO request = new PasswordResetDTOs.ResetPasswordRequestDTO();
        request.setEmail("test@test.com");
        request.setCode("123456");
        request.setNewPassword("newpass");

        ResponseEntity<Void> response = api.resetPassword(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void exchangeToken_validatesClientCredentials() {
        TokenExchangeRequestDTO request = new TokenExchangeRequestDTO();
        request.setExternalToken("ext-token");

        AratiriSecurityProperties.TokenExchange tokenExProps =
                new AratiriSecurityProperties.TokenExchange();
        tokenExProps.setClientId("client-id");
        tokenExProps.setClientSecret("client-secret");
        tokenExProps.setEnabled(true);
        when(securityProperties.getTokenExchange()).thenReturn(tokenExProps);
        AuthTokens tokens = new AuthTokens("access-token", "refresh-token");
        when(tokenExchangePort.exchange("ext-token")).thenReturn(tokens);

        String authHeader = "Basic " + java.util.Base64.getEncoder()
                .encodeToString("client-id:client-secret".getBytes());
        ResponseEntity<AuthResponseDTO> response = api.exchangeToken(authHeader, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void exchangeToken_throwsOnInvalidCredentials() {
        TokenExchangeRequestDTO request = new TokenExchangeRequestDTO();
        request.setExternalToken("ext-token");

        assertThrows(AratiriException.class, () -> api.exchangeToken(null, request));
        assertThrows(AratiriException.class, () -> api.exchangeToken("Bearer xyz", request));
    }
}
