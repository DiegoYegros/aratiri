package com.aratiri.auth.infrastructure.security;

import com.aratiri.accounts.application.dto.CreateAccountRequestDTO;
import com.aratiri.accounts.application.port.in.AccountsPort;
import com.aratiri.auth.application.port.out.LoadUserPort;
import com.aratiri.auth.application.port.out.UserCommandPort;
import com.aratiri.auth.domain.AuthProvider;
import com.aratiri.auth.domain.AuthUser;
import com.aratiri.auth.domain.Role;
import com.aratiri.infrastructure.configuration.security.AratiriSecurityProperties;
import com.aratiri.infrastructure.configuration.security.AratiriSecurityProperties.TrustedIssuer;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Optional;

@Component
public class AratiriJwtPrincipalService {

    private final AratiriSecurityProperties securityProperties;
    private final LoadUserPort loadUserPort;
    private final UserCommandPort userCommandPort;
    private final AccountsPort accountsPort;

    public AratiriJwtPrincipalService(
            AratiriSecurityProperties securityProperties,
            LoadUserPort loadUserPort,
            UserCommandPort userCommandPort,
            @Lazy AccountsPort accountsPort
    ) {
        this.securityProperties = securityProperties;
        this.loadUserPort = loadUserPort;
        this.userCommandPort = userCommandPort;
        this.accountsPort = accountsPort;
    }

    public AuthUser resolveUser(Jwt jwt) {
        TrustedIssuer issuerConfig = securityProperties.resolveByUri(jwt.getIssuer())
                .orElse(null);
        if (issuerConfig != null && !issuerConfig.isAudienceAllowed(jwt.getAudience())) {
            throw new JwtException("Token audience is not permitted");
        }

        String principalClaim = issuerConfig != null && StringUtils.hasText(issuerConfig.getPrincipalClaim())
                ? issuerConfig.getPrincipalClaim()
                : securityProperties.getDefaultPrincipalClaim();

        String principal = tryReadClaim(jwt, principalClaim);
        if (!StringUtils.hasText(principal)) {
            principal = jwt.getSubject();
        }

        if (!StringUtils.hasText(principal)) {
            throw new JwtException("Unable to resolve principal from token");
        }

        String normalizedPrincipal = principal.trim().toLowerCase();
        Optional<AuthUser> existing = loadUserPort.findByEmail(normalizedPrincipal);
        if (existing.isPresent()) {
            AuthUser user = existing.get();
            if (issuerConfig != null && user.provider() != issuerProvider(issuerConfig)) {
                throw new JwtException("Token identity provider does not match existing user account");
            }
            return user;
        }

        if (issuerConfig == null || !issuerConfig.isAutoProvisionUser()) {
            throw new JwtException("User is not registered in Aratiri");
        }

        String displayNameClaim = issuerConfig.getNameClaim();
        String displayName = tryReadClaim(jwt, displayNameClaim);
        if (!StringUtils.hasText(displayName)) {
            displayName = principal;
        }

        Role role = issuerConfig.getDefaultRole() == null ? Role.USER : issuerConfig.getDefaultRole();
        AuthProvider provider = issuerProvider(issuerConfig);
        AuthUser newUser = userCommandPort.registerSocialUser(displayName, normalizedPrincipal, provider, role);

        if (issuerConfig.isAutoProvisionAccount()) {
            CreateAccountRequestDTO createAccountRequestDTO = new CreateAccountRequestDTO();
            createAccountRequestDTO.setUserId(newUser.id());
            accountsPort.createAccount(createAccountRequestDTO, newUser.id());
        }

        return newUser;
    }

    private AuthProvider issuerProvider(TrustedIssuer issuerConfig) {
        return issuerConfig.getProvider() == null ? AuthProvider.EXTERNAL : issuerConfig.getProvider();
    }

    private String tryReadClaim(Jwt jwt, String claimName) {
        if (!StringUtils.hasText(claimName)) {
            return null;
        }
        Object value = jwt.getClaims().get(claimName);
        return value == null ? null : value.toString();
    }
}
