package com.aratiri.infrastructure.configuration.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AratiriSecurityPropertiesTest {

    @Test
    void trustedIssuers_defaultToEmptyList() {
        AratiriSecurityProperties props = new AratiriSecurityProperties();
        assertNotNull(props.getTrustedIssuers());
        assertTrue(props.getTrustedIssuers().isEmpty());
    }

    @Test
    void resolveByIssuer_findsMatchingIssuer() {
        AratiriSecurityProperties props = new AratiriSecurityProperties();
        AratiriSecurityProperties.TrustedIssuer issuer = new AratiriSecurityProperties.TrustedIssuer();
        issuer.setIssuer("https://accounts.google.com");
        props.setTrustedIssuers(List.of(issuer));

        var result = props.resolveByIssuer("https://accounts.google.com");
        assertTrue(result.isPresent());
    }

    @Test
    void resolveByIssuer_nullIssuer_returnsEmpty() {
        AratiriSecurityProperties props = new AratiriSecurityProperties();
        var result = props.resolveByIssuer((String) null);
        assertFalse(result.isPresent());
    }

    @Test
    void resolveByIssuer_emptyIssuer_returnsEmpty() {
        AratiriSecurityProperties props = new AratiriSecurityProperties();
        var result = props.resolveByIssuer("");
        assertFalse(result.isPresent());
    }

    @Test
    void resolveByIssuer_noMatch_returnsEmpty() {
        AratiriSecurityProperties props = new AratiriSecurityProperties();
        AratiriSecurityProperties.TrustedIssuer issuer = new AratiriSecurityProperties.TrustedIssuer();
        issuer.setIssuer("https://accounts.google.com");
        props.setTrustedIssuers(List.of(issuer));

        var result = props.resolveByIssuer("https://unknown.example.com");
        assertFalse(result.isPresent());
    }

    @Test
    void resolveByUri_findsMatch() throws MalformedURLException {
        AratiriSecurityProperties props = new AratiriSecurityProperties();
        AratiriSecurityProperties.TrustedIssuer issuer = new AratiriSecurityProperties.TrustedIssuer();
        issuer.setIssuer("https://accounts.google.com");
        props.setTrustedIssuers(List.of(issuer));

        var result = props.resolveByUri(URI.create("https://accounts.google.com").toURL());
        assertTrue(result.isPresent());
    }

    @Test
    void resolveByUri_nullUrl_returnsEmpty() {
        AratiriSecurityProperties props = new AratiriSecurityProperties();
        var result = props.resolveByUri(null);
        assertFalse(result.isPresent());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://accounts.google.com",
            "HTTPS://ACCOUNTS.GOOGLE.COM",
            "  https://accounts.google.com  "
    })
    void trustedIssuer_matchesIssuer(String tokenIssuer) {
        AratiriSecurityProperties.TrustedIssuer issuer = new AratiriSecurityProperties.TrustedIssuer();
        issuer.setIssuer("https://accounts.google.com");

        assertTrue(issuer.matchesIssuer(tokenIssuer));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "https://other.example.com")
    void trustedIssuer_doesNotMatchIssuer(String tokenIssuer) {
        AratiriSecurityProperties.TrustedIssuer issuer = new AratiriSecurityProperties.TrustedIssuer();
        issuer.setIssuer("https://accounts.google.com");

        assertFalse(issuer.matchesIssuer(tokenIssuer));
    }

    @Test
    void trustedIssuer_matchesByIssuerUri() {
        AratiriSecurityProperties.TrustedIssuer issuer = new AratiriSecurityProperties.TrustedIssuer();
        issuer.setIssuerUri("https://accounts.google.com");
        assertTrue(issuer.matchesIssuer("https://accounts.google.com"));
    }

    @Test
    void trustedIssuer_matchesByIssuerUri_caseInsensitive() {
        AratiriSecurityProperties.TrustedIssuer issuer = new AratiriSecurityProperties.TrustedIssuer();
        issuer.setIssuerUri("HTTPS://ACCOUNTS.GOOGLE.COM");
        assertTrue(issuer.matchesIssuer("https://accounts.google.com"));
    }

    @Test
    void trustedIssuer_noMatch_whenNeitherIssuerNorUriMatch() {
        AratiriSecurityProperties.TrustedIssuer issuer = new AratiriSecurityProperties.TrustedIssuer();
        issuer.setIssuer("https://accounts.google.com");
        issuer.setIssuerUri("https://auth.google.com");
        assertFalse(issuer.matchesIssuer("https://unknown.example.com"));
    }

    @Test
    void trustedIssuer_isAudienceAllowed_emptyAudience_returnsTrue() {
        AratiriSecurityProperties.TrustedIssuer issuer = new AratiriSecurityProperties.TrustedIssuer();
        assertTrue(issuer.isAudienceAllowed(List.of("any_audience")));
    }

    @Test
    void trustedIssuer_isAudienceAllowed_nullTokenAudiences_returnsFalse() {
        AratiriSecurityProperties.TrustedIssuer issuer = new AratiriSecurityProperties.TrustedIssuer();
        issuer.setAudience(List.of("my-app"));
        assertFalse(issuer.isAudienceAllowed(null));
    }

    @Test
    void trustedIssuer_isAudienceAllowed_emptyTokenAudiences_returnsFalse() {
        AratiriSecurityProperties.TrustedIssuer issuer = new AratiriSecurityProperties.TrustedIssuer();
        issuer.setAudience(List.of("my-app"));
        assertFalse(issuer.isAudienceAllowed(List.of()));
    }

    @Test
    void trustedIssuer_isAudienceAllowed_matchingAudience() {
        AratiriSecurityProperties.TrustedIssuer issuer = new AratiriSecurityProperties.TrustedIssuer();
        issuer.setAudience(List.of("my-app"));
        assertTrue(issuer.isAudienceAllowed(List.of("my-app")));
    }

    @Test
    void trustedIssuer_isAudienceAllowed_caseInsensitive() {
        AratiriSecurityProperties.TrustedIssuer issuer = new AratiriSecurityProperties.TrustedIssuer();
        issuer.setAudience(List.of("MY-APP"));
        assertTrue(issuer.isAudienceAllowed(List.of("my-app")));
    }

    @Test
    void trustedIssuer_isAudienceAllowed_noMatchingAudience() {
        AratiriSecurityProperties.TrustedIssuer issuer = new AratiriSecurityProperties.TrustedIssuer();
        issuer.setAudience(List.of("my-app"));
        assertFalse(issuer.isAudienceAllowed(List.of("other-app")));
    }

    @Test
    void trustedIssuer_defaultValues() {
        AratiriSecurityProperties.TrustedIssuer issuer = new AratiriSecurityProperties.TrustedIssuer();
        assertTrue(issuer.isAutoProvisionUser());
        assertTrue(issuer.isAutoProvisionAccount());
        assertEquals("email", issuer.getPrincipalClaim());
        assertEquals("name", issuer.getNameClaim());
    }

    @Test
    void tokenExchange_defaults() {
        AratiriSecurityProperties properties = new AratiriSecurityProperties();
        assertNotNull(properties.getTokenExchange());
        assertFalse(properties.getTokenExchange().isEnabled());
    }

    @Test
    void tokenExchange_setEnabled() {
        AratiriSecurityProperties properties = new AratiriSecurityProperties();
        properties.getTokenExchange().setEnabled(true);
        properties.getTokenExchange().setClientId("client-id");
        properties.getTokenExchange().setClientSecret("client-secret");
        assertTrue(properties.getTokenExchange().isEnabled());
        assertEquals("client-id", properties.getTokenExchange().getClientId());
        assertEquals("client-secret", properties.getTokenExchange().getClientSecret());
    }

    @Test
    void devEndpoints_defaultsToProfileControlledH2Console() {
        AratiriSecurityProperties properties = new AratiriSecurityProperties();
        assertNotNull(properties.getDevEndpoints());
        assertNull(properties.getDevEndpoints().getH2ConsoleEnabled());
    }

    @Test
    void devEndpoints_setH2ConsoleEnabled() {
        AratiriSecurityProperties properties = new AratiriSecurityProperties();
        properties.getDevEndpoints().setH2ConsoleEnabled(true);
        assertTrue(properties.getDevEndpoints().getH2ConsoleEnabled());
    }

    @Test
    void apiDocs_defaultsDisabled() {
        AratiriSecurityProperties properties = new AratiriSecurityProperties();
        assertNotNull(properties.getApiDocs());
        assertFalse(properties.getApiDocs().isEnabled());
    }

    @Test
    void apiDocs_setEnabled() {
        AratiriSecurityProperties properties = new AratiriSecurityProperties();
        properties.getApiDocs().setEnabled(true);
        assertTrue(properties.getApiDocs().isEnabled());
    }

    @Test
    void authRateLimit_defaultsEnabledWithPositiveBounds() {
        AratiriSecurityProperties properties = new AratiriSecurityProperties();
        AratiriSecurityProperties.AuthRateLimit rateLimit = properties.getAuthRateLimit();
        assertTrue(rateLimit.isEnabled());
        assertEquals(30, rateLimit.getRequestsPerWindow());
        assertEquals(java.time.Duration.ofMinutes(1), rateLimit.getWindow());
        assertEquals(100_000L, rateLimit.getMaximumKeys());
        assertDoesNotThrow(rateLimit::validate);
    }

    @Test
    void authRateLimit_validateRejectsNonsensicalValues() {
        AratiriSecurityProperties.AuthRateLimit rateLimit = new AratiriSecurityProperties.AuthRateLimit();
        rateLimit.setRequestsPerWindow(0);
        assertThrows(IllegalStateException.class, rateLimit::validate);

        rateLimit = new AratiriSecurityProperties.AuthRateLimit();
        rateLimit.setWindow(java.time.Duration.ZERO);
        assertThrows(IllegalStateException.class, rateLimit::validate);

        rateLimit = new AratiriSecurityProperties.AuthRateLimit();
        rateLimit.setMaximumKeys(0);
        assertThrows(IllegalStateException.class, rateLimit::validate);
    }

    @Test
    void authRateLimit_validateRejectsSubMillisecondAndHugeWindows() {
        AratiriSecurityProperties.AuthRateLimit subMillis = new AratiriSecurityProperties.AuthRateLimit();
        subMillis.setWindow(java.time.Duration.ofNanos(500_000));
        assertThrows(IllegalStateException.class, subMillis::validate);
        assertThrows(IllegalStateException.class, subMillis::validatedWindowMillis);

        AratiriSecurityProperties.AuthRateLimit aboveMax = new AratiriSecurityProperties.AuthRateLimit();
        aboveMax.setWindow(java.time.Duration.ofDays(1).plusMillis(1));
        assertThrows(IllegalStateException.class, aboveMax::validate);

        AratiriSecurityProperties.AuthRateLimit overflowing = new AratiriSecurityProperties.AuthRateLimit();
        overflowing.setWindow(java.time.Duration.ofSeconds(Long.MAX_VALUE));
        assertThrows(IllegalStateException.class, overflowing::validate);
    }

    @Test
    void authRateLimit_validatedWindowMillisAcceptsBounds() {
        AratiriSecurityProperties.AuthRateLimit min = new AratiriSecurityProperties.AuthRateLimit();
        min.setWindow(AratiriSecurityProperties.AuthRateLimit.MIN_WINDOW);
        assertEquals(1L, min.validatedWindowMillis());

        AratiriSecurityProperties.AuthRateLimit max = new AratiriSecurityProperties.AuthRateLimit();
        max.setWindow(AratiriSecurityProperties.AuthRateLimit.MAX_WINDOW);
        assertEquals(AratiriSecurityProperties.AuthRateLimit.MAX_WINDOW_MILLIS, max.validatedWindowMillis());
    }

    @Test
    void defaultPrincipalClaim_defaultValue() {
        AratiriSecurityProperties props = new AratiriSecurityProperties();
        assertEquals("email", props.getDefaultPrincipalClaim());
    }

    @Test
    void defaultPrincipalClaim_setAndGet() {
        AratiriSecurityProperties props = new AratiriSecurityProperties();
        props.setDefaultPrincipalClaim("sub");
        assertEquals("sub", props.getDefaultPrincipalClaim());
    }
}
