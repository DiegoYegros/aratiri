package com.aratiri;

import com.aratiri.auth.application.port.out.EmailNotificationPort;
import com.aratiri.accounts.application.port.out.CurrencyConversionPort;
import com.aratiri.accounts.application.port.out.LightningAddressPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ApiSecurityIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean
    private EmailNotificationPort emailNotificationPort;

    @MockitoBean
    private CurrencyConversionPort currencyConversionPort;

    @MockitoBean
    private LightningAddressPort lightningAddressPort;

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
    @DisplayName("LNURL public endpoints accessible without authentication")
    void lnurl_public_endpoints_accessible() {
        webTestClient().get().uri("/.well-known/lnurlp/testalias")
                .exchange()
                .expectStatus().value(status -> {
                    assertNotEquals(401, status);
                    assertNotEquals(403, status);
                });
    }

    @Test
    @DisplayName("Swagger endpoints accessible without authentication")
    void swagger_endpoints_accessible() {
        webTestClient().get().uri("/swagger-ui.html")
                .exchange()
                .expectStatus().is3xxRedirection();

        webTestClient().get().uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk();
    }
}
