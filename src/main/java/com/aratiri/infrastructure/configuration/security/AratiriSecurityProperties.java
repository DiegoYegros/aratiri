package com.aratiri.infrastructure.configuration.security;

import com.aratiri.auth.domain.AuthProvider;
import com.aratiri.auth.domain.Role;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Setter
@Getter
@ConfigurationProperties(prefix = "aratiri.security")
public class AratiriSecurityProperties {

    private List<TrustedIssuer> trustedIssuers = new ArrayList<>();
    private String defaultPrincipalClaim = "email";
    private TokenExchange tokenExchange = new TokenExchange();
    private DevEndpoints devEndpoints = new DevEndpoints();
    private ApiDocs apiDocs = new ApiDocs();
    private AuthRateLimit authRateLimit = new AuthRateLimit();

    public Optional<TrustedIssuer> resolveByIssuer(String issuer) {
        if (!StringUtils.hasText(issuer)) {
            return Optional.empty();
        }
        return trustedIssuers.stream()
                .filter(candidate -> candidate.matchesIssuer(issuer))
                .findFirst();
    }

    public Optional<TrustedIssuer> resolveByUri(URL issuer) {
        return resolveByIssuer(issuer == null ? null : issuer.toString());
    }


    @Setter
    @Getter
    public static class TrustedIssuer {
        private String issuer;
        private String jwkSetUri;
        private String issuerUri;
        private String principalClaim = "email";
        private String nameClaim = "name";
        private boolean autoProvisionUser = true;
        private boolean autoProvisionAccount = true;
        private List<String> audience = new ArrayList<>();
        private AuthProvider provider = AuthProvider.EXTERNAL;
        private Role defaultRole = Role.USER;

        boolean matchesIssuer(String tokenIssuer) {
            if (!StringUtils.hasText(tokenIssuer)) {
                return false;
            }
            String normalizedTokenIssuer = normalize(tokenIssuer);
            if (StringUtils.hasText(this.issuer) && normalize(this.issuer).equals(normalizedTokenIssuer)) {
                return true;
            }
            return StringUtils.hasText(this.issuerUri) && normalize(this.issuerUri).equals(normalizedTokenIssuer);
        }

        public boolean isAudienceAllowed(List<String> tokenAudiences) {
            if (CollectionUtils.isEmpty(this.audience)) {
                return true;
            }
            if (CollectionUtils.isEmpty(tokenAudiences)) {
                return false;
            }
            return tokenAudiences.stream()
                    .map(this::normalize)
                    .anyMatch(candidate -> this.audience.stream()
                            .map(this::normalize)
                            .anyMatch(candidate::equals));
        }

        private String normalize(String value) {
            return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
        }
    }

    @Setter
    @Getter
    public static class TokenExchange {
        private boolean enabled = false;
        private String clientId;
        private String clientSecret;

    }

    @Setter
    @Getter
    public static class DevEndpoints {
        private Boolean h2ConsoleEnabled;
    }

    @Setter
    @Getter
    public static class ApiDocs {
        private boolean enabled = false;
    }

    /**
     * In-process throttling for sensitive public auth routes.
     * <p>
     * Defaults to enabled. This is a per-JVM guardrail (not a distributed quota): counters
     * reset on restart, are not shared across instances, and use {@code HttpServletRequest#getRemoteAddr()}
     * only — spoofable {@code X-Forwarded-For} is ignored unless a trusted reverse-proxy
     * rewrites the remote address. Disable only behind a gateway/WAF that already enforces
     * equivalent limits, or for tightly controlled local testing.
     * <p>
     * Supported {@code window} range: {@link #MIN_WINDOW} ({@code 1ms}) through
     * {@link #MAX_WINDOW} ({@code 1d}). Values below 1ms (including positive sub-millisecond
     * durations that truncate to 0ms) and values above 1d (or that overflow millisecond
     * conversion / expiry arithmetic) fail startup validation.
     */
    @Setter
    @Getter
    public static class AuthRateLimit {
        /** Inclusive lower bound: at least one full millisecond so fixed-window math is meaningful. */
        public static final Duration MIN_WINDOW = Duration.ofMillis(1);
        /**
         * Inclusive upper bound for an auth abuse window. Keeps {@code windowMs * 2} expiry
         * arithmetic and retry-after calculations inside {@code long} without overflow.
         */
        public static final Duration MAX_WINDOW = Duration.ofDays(1);
        public static final long MIN_WINDOW_MILLIS = 1L;
        public static final long MAX_WINDOW_MILLIS = MAX_WINDOW.toMillis();

        private boolean enabled = true;
        private int requestsPerWindow = 30;
        private Duration window = Duration.ofMinutes(1);
        private long maximumKeys = 100_000L;

        public void validate() {
            if (requestsPerWindow < 1) {
                throw new IllegalStateException(
                        "aratiri.security.auth-rate-limit.requests-per-window must be >= 1");
            }
            validatedWindowMillis();
            if (maximumKeys < 1L) {
                throw new IllegalStateException(
                        "aratiri.security.auth-rate-limit.maximum-keys must be >= 1");
            }
        }

        /**
         * Validates {@code window} and returns its millisecond length for safe limiter arithmetic.
         * Call once at construction; do not re-convert per request.
         */
        public long validatedWindowMillis() {
            if (window == null || window.isNegative() || window.isZero()) {
                throw new IllegalStateException(
                        "aratiri.security.auth-rate-limit.window must be a positive duration "
                                + "between " + MIN_WINDOW + " and " + MAX_WINDOW + " inclusive");
            }
            long millis;
            try {
                millis = window.toMillis();
            } catch (ArithmeticException ex) {
                throw new IllegalStateException(
                        "aratiri.security.auth-rate-limit.window overflows millisecond conversion: "
                                + window,
                        ex);
            }
            // Positive sub-millisecond values (e.g. 500µs) truncate to 0 via toMillis().
            if (millis < MIN_WINDOW_MILLIS) {
                throw new IllegalStateException(
                        "aratiri.security.auth-rate-limit.window must be >= " + MIN_WINDOW
                                + " (got " + window + ")");
            }
            if (millis > MAX_WINDOW_MILLIS) {
                throw new IllegalStateException(
                        "aratiri.security.auth-rate-limit.window must be <= " + MAX_WINDOW
                                + " (got " + window + ")");
            }
            // expireAfterAccess uses 2x window; reject anything that cannot multiply safely.
            if (millis > Long.MAX_VALUE / 2L) {
                throw new IllegalStateException(
                        "aratiri.security.auth-rate-limit.window is too large for expiry arithmetic: "
                                + window);
            }
            return millis;
        }
    }
}
