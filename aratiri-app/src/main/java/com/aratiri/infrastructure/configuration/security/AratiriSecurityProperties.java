package com.aratiri.infrastructure.configuration.security;

import com.aratiri.auth.domain.AuthProvider;
import com.aratiri.auth.domain.Role;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URL;
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
}
