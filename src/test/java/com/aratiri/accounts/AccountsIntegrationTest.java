package com.aratiri.accounts;

import com.aratiri.AbstractIntegrationTest;
import com.aratiri.auth.application.dto.AuthResponseDTO;
import com.aratiri.auth.application.dto.RegistrationRequestDTO;
import com.aratiri.auth.application.dto.VerificationRequestDTO;
import com.aratiri.auth.application.port.out.EmailNotificationPort;
import com.aratiri.auth.domain.AuthProvider;
import com.aratiri.auth.domain.Role;
import com.aratiri.auth.infrastructure.jwt.JwtUtil;
import com.aratiri.accounts.application.dto.AccountDTO;
import com.aratiri.accounts.application.port.out.CurrencyConversionPort;
import com.aratiri.accounts.application.port.out.LightningAddressPort;
import com.aratiri.infrastructure.persistence.jpa.entity.UserEntity;
import com.aratiri.infrastructure.persistence.jpa.repository.UserRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.VerificationDataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

class AccountsIntegrationTest extends AbstractIntegrationTest {

    private static final AtomicInteger USER_SEQ = new AtomicInteger();

    @MockitoBean
    private EmailNotificationPort emailNotificationPort;

    @MockitoBean
    private CurrencyConversionPort currencyConversionPort;

    @MockitoBean
    private LightningAddressPort lightningAddressPort;

    @Autowired
    private VerificationDataRepository verificationDataRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    private String accessToken;
    private AccountDTO callerAccount;

    @BeforeEach
    void authenticateUser() {
        when(currencyConversionPort.getCurrentBtcPrice()).thenReturn(Map.of("usd", BigDecimal.valueOf(50000)));
        when(lightningAddressPort.generateTaprootAddress())
                .thenAnswer(invocation -> "bc1p_test_address_" + USER_SEQ.incrementAndGet());
        doAnswer(invocation -> null).when(emailNotificationPort).sendVerificationEmail(anyString(), anyString());

        this.accessToken = registerAndVerify("Accounts Test", uniqueEmail("accounts-test"), "accountstest");
        this.callerAccount = fetchOwnAccount(accessToken);
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
    @DisplayName("USER cannot look up own account by arbitrary account ID")
    void user_cannot_lookup_own_account_by_id() {
        webTestClient().get().uri("/v1/accounts/account/" + callerAccount.getId())
                .header("Authorization", "Bearer " + accessToken)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    @DisplayName("USER cannot look up own account by user ID route")
    void user_cannot_lookup_own_account_by_user_id() {
        webTestClient().get().uri("/v1/accounts/account/user/" + callerAccount.getUserId())
                .header("Authorization", "Bearer " + accessToken)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    @DisplayName("USER cannot look up another user's account by ID or user ID")
    void user_cannot_lookup_other_user_account() {
        String otherToken = registerAndVerify("Other User", uniqueEmail("accounts-other"), "accountsother");
        AccountDTO otherAccount = fetchOwnAccount(otherToken);

        webTestClient().get().uri("/v1/accounts/account/" + otherAccount.getId())
                .header("Authorization", "Bearer " + accessToken)
                .exchange()
                .expectStatus().isForbidden();

        webTestClient().get().uri("/v1/accounts/account/user/" + otherAccount.getUserId())
                .header("Authorization", "Bearer " + accessToken)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    @DisplayName("VIEWER cannot use arbitrary account lookup routes")
    void viewer_cannot_use_arbitrary_lookup_routes() {
        String viewerToken = bearerTokenForPersistedRole(Role.VIEWER, "accounts-viewer@example.com", "Accounts Viewer");

        webTestClient().get().uri("/v1/accounts/account/" + callerAccount.getId())
                .header("Authorization", viewerToken)
                .exchange()
                .expectStatus().isForbidden();

        webTestClient().get().uri("/v1/accounts/account/user/" + callerAccount.getUserId())
                .header("Authorization", viewerToken)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    @DisplayName("ADMIN can look up another user's account by ID and user ID")
    void admin_can_lookup_other_user_account() {
        String otherToken = registerAndVerify("Target User", uniqueEmail("accounts-target"), "accountstarget");
        AccountDTO targetAccount = fetchOwnAccount(otherToken);
        String adminToken = bearerTokenForPersistedRole(Role.ADMIN, "accounts-admin@example.com", "Accounts Admin");

        webTestClient().get().uri("/v1/accounts/account/" + targetAccount.getId())
                .header("Authorization", adminToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody(AccountDTO.class)
                .value(response -> {
                    assertNotNull(response);
                    assertEquals(targetAccount.getId(), response.getId());
                    assertEquals(targetAccount.getUserId(), response.getUserId());
                });

        webTestClient().get().uri("/v1/accounts/account/user/" + targetAccount.getUserId())
                .header("Authorization", adminToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody(AccountDTO.class)
                .value(response -> {
                    assertNotNull(response);
                    assertEquals(targetAccount.getId(), response.getId());
                    assertEquals(targetAccount.getUserId(), response.getUserId());
                });
    }

    @Test
    @DisplayName("SUPERADMIN inherits access to arbitrary account lookup routes")
    void superadmin_inherits_arbitrary_lookup_access() {
        String otherToken = registerAndVerify("Super Target", uniqueEmail("accounts-super-target"), "accountssuptgt");
        AccountDTO targetAccount = fetchOwnAccount(otherToken);
        String superAdminToken = bearerTokenForPersistedRole(
                Role.SUPERADMIN, "accounts-superadmin@example.com", "Accounts Superadmin");

        webTestClient().get().uri("/v1/accounts/account/" + targetAccount.getId())
                .header("Authorization", superAdminToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody(AccountDTO.class)
                .value(response -> assertEquals(targetAccount.getId(), response.getId()));

        webTestClient().get().uri("/v1/accounts/account/user/" + targetAccount.getUserId())
                .header("Authorization", superAdminToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody(AccountDTO.class)
                .value(response -> assertEquals(targetAccount.getUserId(), response.getUserId()));
    }

    @Test
    @DisplayName("Unauthenticated request to accounts endpoint returns 401")
    void unauthenticated_request_returns_401() {
        webTestClient().get().uri("/v1/accounts/account")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("Unauthenticated arbitrary account lookup returns 401")
    void unauthenticated_arbitrary_lookup_returns_401() {
        webTestClient().get().uri("/v1/accounts/account/" + callerAccount.getId())
                .exchange()
                .expectStatus().isUnauthorized();

        webTestClient().get().uri("/v1/accounts/account/user/" + callerAccount.getUserId())
                .exchange()
                .expectStatus().isUnauthorized();
    }

    private String registerAndVerify(String name, String email, String alias) {
        String password = "SecurePass123!";

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
        return verifiedTokens.getAccessToken();
    }

    private AccountDTO fetchOwnAccount(String token) {
        AccountDTO account = webTestClient().get().uri("/v1/accounts/account")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(AccountDTO.class)
                .returnResult().getResponseBody();
        assertNotNull(account);
        return account;
    }

    private String bearerTokenForPersistedRole(Role role, String email, String name) {
        UserEntity user = new UserEntity();
        user.setName(name);
        user.setEmail(email);
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setRole(role);
        userRepository.save(user);
        return "Bearer " + jwtUtil.generateToken(email);
    }

    private static String uniqueEmail(String prefix) {
        return prefix + "-" + USER_SEQ.incrementAndGet() + "@example.com";
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
