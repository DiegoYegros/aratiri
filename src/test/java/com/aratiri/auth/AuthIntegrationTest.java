package com.aratiri.auth;

import com.aratiri.AbstractIntegrationTest;
import com.aratiri.auth.application.dto.AuthRequestDTO;
import com.aratiri.auth.application.dto.AuthResponseDTO;
import com.aratiri.auth.application.dto.RegistrationRequestDTO;
import com.aratiri.auth.application.dto.VerificationRequestDTO;
import com.aratiri.auth.application.port.out.EmailNotificationPort;
import com.aratiri.accounts.application.port.out.CurrencyConversionPort;
import com.aratiri.accounts.application.port.out.LightningAddressPort;
import com.aratiri.infrastructure.persistence.jpa.entity.PasswordResetData;
import com.aratiri.infrastructure.persistence.jpa.entity.UserEntity;
import com.aratiri.infrastructure.persistence.jpa.repository.PasswordResetDataRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.UserRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.VerificationDataRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

class AuthIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean
    private EmailNotificationPort emailNotificationPort;

    @MockitoBean
    private CurrencyConversionPort currencyConversionPort;

    @MockitoBean
    private LightningAddressPort lightningAddressPort;

    @Autowired
    private VerificationDataRepository verificationDataRepository;

    @Autowired
    private PasswordResetDataRepository passwordResetDataRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("Full registration flow: register -> verify -> login -> access /me")
    void register_verify_login_flow() {
        String email = "integration-test@example.com";
        String password = "SecurePass123!";
        String name = "Integration Test";
        String alias = "testuser1";

        when(currencyConversionPort.getCurrentBtcPrice()).thenReturn(Map.of("usd", BigDecimal.valueOf(50000)));
        when(lightningAddressPort.generateTaprootAddress()).thenReturn("bc1p_test_address");
        doAnswer(invocation -> null).when(emailNotificationPort).sendVerificationEmail(anyString(), anyString());

        webTestClient().post().uri("/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createRegistrationRequest(name, email, password, alias))
                .exchange()
                .expectStatus().isCreated();

        String verificationCode = verificationDataRepository.findById(email)
                .orElseThrow()
                .getCode();

        AuthResponseDTO verifiedTokens = webTestClient().post().uri("/v1/auth/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createVerificationRequest(email, verificationCode))
                .exchange()
                .expectStatus().isOk()
                .expectBody(AuthResponseDTO.class)
                .returnResult().getResponseBody();

        assertNotNull(verifiedTokens);
        assertNotNull(verifiedTokens.getAccessToken());
        assertNotNull(verifiedTokens.getRefreshToken());

        var user = userRepository.findByEmail(email).orElseThrow();
        assertNotNull(user.getPassword(), "User password should be set");
        assertTrue(user.getPassword().startsWith("$2a$") || user.getPassword().startsWith("$2b$"), "Password should be BCrypt encoded");

        String accessToken = verifiedTokens.getAccessToken();

        AuthResponseDTO loginResponse = webTestClient().post().uri("/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createLoginRequest(email, password))
                .exchange()
                .expectStatus().isOk()
                .expectBody(AuthResponseDTO.class)
                .returnResult().getResponseBody();

        assertNotNull(loginResponse, "Login response should not be null");

        webTestClient().get().uri("/v1/auth/me")
                .header("Authorization", "Bearer " + accessToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo(name)
                .jsonPath("$.email").isEqualTo(email);
    }

    @Test
    @DisplayName("Login rejects wrong password")
    void login_rejects_wrong_password() {
        String email = "wrong-pass@example.com";
        String password = "SecurePass123!";
        String name = "Wrong Pass User";
        String alias = "wrongpass";

        when(currencyConversionPort.getCurrentBtcPrice()).thenReturn(Map.of("usd", BigDecimal.valueOf(50000)));
        when(lightningAddressPort.generateTaprootAddress()).thenReturn("bc1p_test_address");
        doAnswer(invocation -> null).when(emailNotificationPort).sendVerificationEmail(anyString(), anyString());

        webTestClient().post().uri("/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createRegistrationRequest(name, email, password, alias))
                .exchange()
                .expectStatus().isCreated();

        String verificationCode = verificationDataRepository.findById(email)
                .orElseThrow()
                .getCode();

        webTestClient().post().uri("/v1/auth/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createVerificationRequest(email, verificationCode))
                .exchange()
                .expectStatus().isOk();

        webTestClient().post().uri("/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createLoginRequest(email, "WrongPassword123!"))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("Refresh token flow works end-to-end")
    void refresh_token_flow() {
        String email = "refresh-test@example.com";
        String password = "SecurePass123!";
        String name = "Refresh Test";
        String alias = "refreshtest";

        when(currencyConversionPort.getCurrentBtcPrice()).thenReturn(Map.of("usd", BigDecimal.valueOf(50000)));
        when(lightningAddressPort.generateTaprootAddress()).thenReturn("bc1p_test_address");
        doAnswer(invocation -> null).when(emailNotificationPort).sendVerificationEmail(anyString(), anyString());

        webTestClient().post().uri("/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createRegistrationRequest(name, email, password, alias))
                .exchange()
                .expectStatus().isCreated();

        String verificationCode = verificationDataRepository.findById(email)
                .orElseThrow()
                .getCode();

        AuthResponseDTO verifiedTokens = webTestClient().post().uri("/v1/auth/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createVerificationRequest(email, verificationCode))
                .exchange()
                .expectStatus().isOk()
                .expectBody(AuthResponseDTO.class)
                .returnResult().getResponseBody();

        assertNotNull(verifiedTokens);

        com.aratiri.auth.application.dto.RefreshTokenRequestDTO refreshRequest = new com.aratiri.auth.application.dto.RefreshTokenRequestDTO();
        refreshRequest.setRefreshToken(verifiedTokens.getRefreshToken());

        webTestClient().post().uri("/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(refreshRequest)
                .exchange()
                .expectStatus().isOk()
                .expectBody(AuthResponseDTO.class)
                .value(response -> assertNotNull(response.getAccessToken()));
    }

    @Test
    @DisplayName("Logout deletes refresh token")
    void logout_deletes_refresh_token() {
        String email = "logout-test@example.com";
        String password = "SecurePass123!";
        String name = "Logout Test";
        String alias = "logouttest";

        when(currencyConversionPort.getCurrentBtcPrice()).thenReturn(Map.of("usd", BigDecimal.valueOf(50000)));
        when(lightningAddressPort.generateTaprootAddress()).thenReturn("bc1p_test_address");
        doAnswer(invocation -> null).when(emailNotificationPort).sendVerificationEmail(anyString(), anyString());

        webTestClient().post().uri("/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createRegistrationRequest(name, email, password, alias))
                .exchange()
                .expectStatus().isCreated();

        String verificationCode = verificationDataRepository.findById(email)
                .orElseThrow()
                .getCode();

        AuthResponseDTO verifiedTokens = webTestClient().post().uri("/v1/auth/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createVerificationRequest(email, verificationCode))
                .exchange()
                .expectStatus().isOk()
                .expectBody(AuthResponseDTO.class)
                .returnResult().getResponseBody();

        assertNotNull(verifiedTokens);

        com.aratiri.auth.application.dto.LogoutRequestDTO logoutRequest = new com.aratiri.auth.application.dto.LogoutRequestDTO();
        logoutRequest.setRefreshToken(verifiedTokens.getRefreshToken());

        webTestClient().post().uri("/v1/auth/logout")
                .header("Authorization", "Bearer " + verifiedTokens.getAccessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(logoutRequest)
                .exchange()
                .expectStatus().isOk();

        com.aratiri.auth.application.dto.RefreshTokenRequestDTO refreshRequest = new com.aratiri.auth.application.dto.RefreshTokenRequestDTO();
        refreshRequest.setRefreshToken(verifiedTokens.getRefreshToken());

        webTestClient().post().uri("/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(refreshRequest)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.message").isEqualTo("Refresh token is not in database!");
    }

    @Test
    @DisplayName("Password reset flow: forgot -> reset -> login with new password")
    void password_reset_flow() {
        String email = "reset-test@example.com";
        String originalPassword = "SecurePass123!";
        String newPassword = "NewSecurePass456!";
        String name = "Reset Test";
        String alias = "resettest";

        when(currencyConversionPort.getCurrentBtcPrice()).thenReturn(Map.of("usd", BigDecimal.valueOf(50000)));
        when(lightningAddressPort.generateTaprootAddress()).thenReturn("bc1p_test_address");
        doAnswer(invocation -> null).when(emailNotificationPort).sendVerificationEmail(anyString(), anyString());

        webTestClient().post().uri("/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createRegistrationRequest(name, email, originalPassword, alias))
                .exchange()
                .expectStatus().isCreated();

        String verificationCode = verificationDataRepository.findById(email)
                .orElseThrow()
                .getCode();

        webTestClient().post().uri("/v1/auth/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createVerificationRequest(email, verificationCode))
                .exchange()
                .expectStatus().isOk();

        doAnswer(invocation -> null).when(emailNotificationPort).sendPasswordResetEmail(anyString(), anyString());

        com.aratiri.auth.application.dto.PasswordResetDTOs.ForgotPasswordRequestDTO forgotRequest =
                new com.aratiri.auth.application.dto.PasswordResetDTOs.ForgotPasswordRequestDTO();
        forgotRequest.setEmail(email);

        webTestClient().post().uri("/v1/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(forgotRequest)
                .exchange()
                .expectStatus().isOk();

        UserEntity user = userRepository.findByEmail(email).orElseThrow();
        PasswordResetData resetData = passwordResetDataRepository.findByUser(user).orElseThrow();

        com.aratiri.auth.application.dto.PasswordResetDTOs.ResetPasswordRequestDTO resetRequest =
                new com.aratiri.auth.application.dto.PasswordResetDTOs.ResetPasswordRequestDTO();
        resetRequest.setEmail(email);
        resetRequest.setCode(resetData.getCode());
        resetRequest.setNewPassword(newPassword);

        webTestClient().post().uri("/v1/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(resetRequest)
                .exchange()
                .expectStatus().isOk();

        webTestClient().post().uri("/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createLoginRequest(email, newPassword))
                .exchange()
                .expectStatus().isOk()
                .expectBody(AuthResponseDTO.class)
                .value(response -> assertNotNull(response.getAccessToken()));

        webTestClient().post().uri("/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createLoginRequest(email, originalPassword))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("Unauthenticated request to protected endpoint returns 401")
    void unauthenticated_request_to_protected_endpoint_rejected() {
        webTestClient().get().uri("/v1/auth/me")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    private RegistrationRequestDTO createRegistrationRequest(String name, String email, String password, String alias) {
        RegistrationRequestDTO request = new RegistrationRequestDTO();
        request.setName(name);
        request.setEmail(email);
        request.setPassword(password);
        request.setAlias(alias);
        return request;
    }

    private VerificationRequestDTO createVerificationRequest(String email, String code) {
        VerificationRequestDTO request = new VerificationRequestDTO();
        request.setEmail(email);
        request.setCode(code);
        return request;
    }

    private AuthRequestDTO createLoginRequest(String username, String password) {
        AuthRequestDTO request = new AuthRequestDTO();
        request.setUsername(username);
        request.setPassword(password);
        return request;
    }
}
