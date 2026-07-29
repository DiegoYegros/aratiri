package com.aratiri;

import com.aratiri.accounts.application.port.out.CurrencyConversionPort;
import com.aratiri.accounts.application.port.out.LightningAddressPort;
import com.aratiri.auth.application.dto.AuthRequestDTO;
import com.aratiri.auth.application.port.in.AuthPort;
import com.aratiri.auth.application.port.out.EmailNotificationPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.TestPropertySource;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestPropertySource(properties = {
        "aratiri.security.auth-rate-limit.enabled=true",
        "aratiri.security.auth-rate-limit.requests-per-window=2",
        "aratiri.security.auth-rate-limit.window=1m",
        "aratiri.security.auth-rate-limit.maximum-keys=1000"
})
class AuthRateLimitIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean
    private EmailNotificationPort emailNotificationPort;

    @MockitoBean
    private CurrencyConversionPort currencyConversionPort;

    @MockitoBean
    private LightningAddressPort lightningAddressPort;

    @MockitoSpyBean
    private AuthPort authPort;

    @Test
    @DisplayName("Auth rate limit: 429 short-circuit, XFF ignored, endpoint separation, other routes unchanged")
    void auth_rate_limit_contract() {
        webTestClient().get().uri("/.well-known/lnurlp/testalias")
                .exchange()
                .expectStatus().value(status -> {
                    if (status == 401 || status == 403 || status == 429) {
                        throw new AssertionError("unrelated public route should not be auth-blocked or rate-limited, got " + status);
                    }
                });

        webTestClient().get().uri("/v1/accounts/account")
                .exchange()
                .expectStatus().isUnauthorized();

        webTestClient().get().uri("/v1/auth/me")
                .exchange()
                .expectStatus().isUnauthorized();

        AuthRequestDTO body = new AuthRequestDTO();
        body.setUsername("rate-limit@example.com");
        body.setPassword("not-the-real-password");

        webTestClient().post().uri("/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().value(status -> {
                    if (status == 429) {
                        throw new AssertionError("first login should reach the auth handler");
                    }
                });

        webTestClient().post().uri("/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().value(status -> {
                    if (status == 429) {
                        throw new AssertionError("second login should reach the auth handler");
                    }
                });

        webTestClient().post().uri("/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Forwarded-For", "203.0.113.50")
                .bodyValue(body)
                .exchange()
                .expectStatus().isEqualTo(429)
                .expectHeader().value(HttpHeaders.RETRY_AFTER, value -> {
                    long retryAfter = Long.parseLong(value);
                    assertTrue(retryAfter >= 1L && retryAfter <= 60L,
                            () -> "Retry-After must be in [1, windowSeconds=60], got " + retryAfter);
                })
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.message").isEqualTo("Too many requests. Please try again later.")
                .jsonPath("$.status").isEqualTo(429)
                .jsonPath("$.timestamp").exists();

        verify(authPort, times(2)).login(anyString(), anyString());

        webTestClient().post().uri("/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"refreshToken\":\"not-a-token\"}")
                .exchange()
                .expectStatus().value(status -> {
                    if (status == 429) {
                        throw new AssertionError("refresh must not share the login rate-limit bucket");
                    }
                });
    }
}
