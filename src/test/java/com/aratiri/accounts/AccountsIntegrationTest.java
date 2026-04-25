package com.aratiri.accounts;

import com.aratiri.AbstractIntegrationTest;
import com.aratiri.auth.application.dto.AuthResponseDTO;
import com.aratiri.auth.application.dto.RegistrationRequestDTO;
import com.aratiri.auth.application.dto.VerificationRequestDTO;
import com.aratiri.auth.application.port.out.EmailNotificationPort;
import com.aratiri.accounts.application.dto.AccountDTO;
import com.aratiri.accounts.application.port.out.CurrencyConversionPort;
import com.aratiri.accounts.application.port.out.LightningAddressPort;
import com.aratiri.infrastructure.persistence.jpa.repository.VerificationDataRepository;
import org.junit.jupiter.api.BeforeEach;
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

class AccountsIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean
    private EmailNotificationPort emailNotificationPort;

    @MockitoBean
    private CurrencyConversionPort currencyConversionPort;

    @MockitoBean
    private LightningAddressPort lightningAddressPort;

    @Autowired
    private VerificationDataRepository verificationDataRepository;

    private String accessToken;

    @BeforeEach
    void authenticateUser() {
        String email = "accounts-test@example.com";
        String password = "SecurePass123!";
        String name = "Accounts Test";
        String alias = "accountstest";

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
        this.accessToken = verifiedTokens.getAccessToken();
    }

    @Test
    @DisplayName("Get account returns account with zero balance after registration")
    void getAccount_returns_zero_balance_after_registration() {
        webTestClient().get().uri("/v1/accounts/account")
                .header("Authorization", "Bearer " + accessToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody(AccountDTO.class)
                .value(account -> {
                    assertNotNull(account);
                    assertEquals(0L, account.getBalance());
                    assertNotNull(account.getAlias());
                    assertNotNull(account.getBitcoinAddress());
                });
    }

    @Test
    @DisplayName("Get account by user ID resolves correctly")
    void getAccountByUserId_resolves_correctly() {
        AccountDTO account = webTestClient().get().uri("/v1/accounts/account")
                .header("Authorization", "Bearer " + accessToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody(AccountDTO.class)
                .returnResult().getResponseBody();

        assertNotNull(account);
        String userId = account.getUserId();

        webTestClient().get().uri("/v1/accounts/account/user/" + userId)
                .header("Authorization", "Bearer " + accessToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody(AccountDTO.class)
                .value(response -> {
                    assertNotNull(response);
                    assertEquals(userId, response.getUserId());
                });
    }

    @Test
    @DisplayName("Unauthenticated request to accounts endpoint returns 401")
    void unauthenticated_request_returns_401() {
        webTestClient().get().uri("/v1/accounts/account")
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
}
