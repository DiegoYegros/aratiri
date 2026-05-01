package com.aratiri;

import com.aratiri.accounts.application.port.out.CurrencyConversionPort;
import com.aratiri.accounts.application.port.out.LightningAddressPort;
import com.aratiri.auth.application.port.out.EmailNotificationPort;
import com.aratiri.auth.domain.AuthProvider;
import com.aratiri.auth.domain.Role;
import com.aratiri.auth.infrastructure.jwt.JwtUtil;
import com.aratiri.infrastructure.persistence.jpa.entity.UserEntity;
import com.aratiri.infrastructure.persistence.jpa.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

abstract class AbstractApiSecurityIntegrationTest extends AbstractIntegrationTest {

    private static final String SECURITY_TEST_EMAIL = "security-test@example.com";

    @MockitoBean
    private EmailNotificationPort emailNotificationPort;

    @MockitoBean
    private CurrencyConversionPort currencyConversionPort;

    @MockitoBean
    private LightningAddressPort lightningAddressPort;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    protected String registeredUserAuthorizationHeader() {
        userRepository.findByEmail(SECURITY_TEST_EMAIL)
                .orElseGet(() -> {
                    UserEntity user = new UserEntity();
                    user.setName("Security Test");
                    user.setEmail(SECURITY_TEST_EMAIL);
                    user.setAuthProvider(AuthProvider.LOCAL);
                    user.setRole(Role.USER);
                    return userRepository.save(user);
                });

        return "Bearer " + jwtUtil.generateToken(SECURITY_TEST_EMAIL);
    }
}

class ApiSecurityIntegrationTest extends AbstractApiSecurityIntegrationTest {

    @Test
    @DisplayName("Public auth endpoints accessible without authentication")
    void public_endpoints_accessible_without_auth() {
        String[] publicEndpoints = {
                "/v1/auth/login",
                "/v1/auth/register",
                "/v1/auth/verify",
                "/v1/auth/forgot-password",
                "/v1/auth/reset-password",
                "/v1/auth/refresh",
                "/v1/auth/sso/google"
        };

        for (String endpoint : publicEndpoints) {
            webTestClient().post().uri(endpoint)
                    .exchange()
                    .expectStatus().value(status -> {
                        assertNotEquals(401, status);
                        assertNotEquals(403, status);
                    });
        }
    }

    @Test
    @DisplayName("Protected endpoints reject unauthenticated requests")
    void protected_endpoints_reject_unauthenticated() {
        webTestClient().get().uri("/v1/accounts/account")
                .exchange()
                .expectStatus().isUnauthorized();

        webTestClient().get().uri("/v1/transactions/test-id/confirm")
                .exchange()
                .expectStatus().isUnauthorized();

        webTestClient().post().uri("/v1/invoices")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("Invalid JWT token is rejected")
    void invalid_jwt_rejected() {
        webTestClient().get().uri("/v1/accounts/account")
                .header("Authorization", "Bearer invalid-jwt-token")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("Expired JWT token is rejected")
    void expired_jwt_rejected() {
        String expiredToken = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0QGV4YW1wbGUuY29tIiwiaWF0IjoxMDAwMDAwMDAwLCJleHAiOjEwMDAwMDAwMDF9.expired";

        webTestClient().get().uri("/v1/accounts/account")
                .header("Authorization", "Bearer " + expiredToken)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("Legitimate public endpoints keep intended access behavior")
    void legitimate_public_endpoints_accessible() {
        webTestClient().get().uri("/.well-known/lnurlp/testalias")
                .exchange()
                .expectStatus().value(status -> {
                    assertNotEquals(401, status);
                    assertNotEquals(403, status);
                });

        webTestClient().get().uri("/lnurl/callback/testalias?amount=1000")
                .exchange()
                .expectStatus().value(status -> {
                    assertNotEquals(401, status);
                    assertNotEquals(403, status);
                });

        webTestClient().get().uri("/v1/notifications/subscribe")
                .exchange()
                .expectStatus().value(status -> {
                    assertNotEquals(401, status);
                    assertNotEquals(403, status);
                });
    }

    @Test
    @DisplayName("H2 console route is permitted in test profile")
    void h2_console_permitted_in_test_profile() {
        webTestClient().get().uri("/h2-console/")
                .exchange()
                .expectStatus().value(status -> {
                    assertNotEquals(401, status);
                    assertNotEquals(403, status);
                });
    }

    @Test
    @DisplayName("Swagger endpoints are blocked by default")
    void swagger_endpoints_restricted_by_default() {
        webTestClient().get().uri("/swagger-ui.html")
                .exchange()
                .expectStatus().isUnauthorized();

        webTestClient().get().uri("/v3/api-docs")
                .exchange()
                .expectStatus().isUnauthorized();

        String authorization = registeredUserAuthorizationHeader();

        webTestClient().get().uri("/swagger-ui.html")
                .header("Authorization", authorization)
                .exchange()
                .expectStatus().isForbidden();

        webTestClient().get().uri("/v3/api-docs")
                .header("Authorization", authorization)
                .exchange()
                .expectStatus().isForbidden();
    }
}

@TestPropertySource(properties = {
        "aratiri.security.api-docs.enabled=true",
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=true"
})
class ApiSecurityDocsEnabledIntegrationTest extends AbstractApiSecurityIntegrationTest {

    @Test
    @DisplayName("Swagger endpoints accessible when API docs are explicitly enabled")
    void swagger_endpoints_accessible_when_enabled() {
        webTestClient().get().uri("/swagger-ui.html")
                .exchange()
                .expectStatus().is3xxRedirection();

        webTestClient().get().uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk();
    }
}

@TestPropertySource(properties = "aratiri.security.dev-endpoints.h2-console-enabled=false")
class ApiSecurityDevEndpointsDisabledIntegrationTest extends AbstractApiSecurityIntegrationTest {

    @Test
    @DisplayName("H2 console route is blocked when explicitly disabled")
    void h2_console_restricted_when_disabled() {
        webTestClient().get().uri("/h2-console/")
                .exchange()
                .expectStatus().isUnauthorized();

        webTestClient().get().uri("/h2-console/")
                .header("Authorization", registeredUserAuthorizationHeader())
                .exchange()
                .expectStatus().isForbidden();
    }
}
