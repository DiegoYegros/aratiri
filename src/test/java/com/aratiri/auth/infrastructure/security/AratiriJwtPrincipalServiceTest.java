package com.aratiri.auth.infrastructure.security;

import com.aratiri.accounts.application.port.in.AccountsPort;
import com.aratiri.auth.application.port.out.LoadUserPort;
import com.aratiri.auth.application.port.out.UserCommandPort;
import com.aratiri.auth.domain.AuthProvider;
import com.aratiri.auth.domain.AuthUser;
import com.aratiri.auth.domain.Role;
import com.aratiri.infrastructure.configuration.security.AratiriSecurityProperties;
import com.aratiri.infrastructure.configuration.security.AratiriSecurityProperties.TrustedIssuer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;

import java.net.URL;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AratiriJwtPrincipalServiceTest {

    @Mock
    private AratiriSecurityProperties securityProperties;

    @Mock
    private LoadUserPort loadUserPort;

    @Mock
    private UserCommandPort userCommandPort;

    @Mock
    private AccountsPort accountsPort;

    private AratiriJwtPrincipalService service;

    @BeforeEach
    void setUp() {
        service = new AratiriJwtPrincipalService(securityProperties, loadUserPort, userCommandPort, accountsPort);
    }

    private static Jwt jwtWithClaims(String issuerUrl, Map<String, Object> extraClaims) {
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("alg", "none");
        Map<String, Object> claims = new LinkedHashMap<>(extraClaims);
        if (issuerUrl != null) {
            claims.put("iss", issuerUrl);
        }
        if (!claims.containsKey("sub")) {
            claims.put("sub", "user-sub");
        }
        return new Jwt("token", Instant.now(), Instant.now().plusSeconds(3600), headers, claims);
    }

    @ParameterizedTest
    @MethodSource("trustedIssuerPrincipalClaims")
    void resolveUser_returnsExistingUserByEmail(
            String issuerPrincipalClaim,
            String defaultPrincipalClaim,
            Map<String, Object> claims
    ) {
        Jwt jwt = jwtWithClaims("https://trusted.example.com", claims);
        TrustedIssuer issuer = new TrustedIssuer();
        issuer.setIssuer("https://trusted.example.com");
        issuer.setPrincipalClaim(issuerPrincipalClaim);
        when(securityProperties.resolveByUri(any(URL.class))).thenReturn(Optional.of(issuer));
        if (defaultPrincipalClaim != null) {
            when(securityProperties.getDefaultPrincipalClaim()).thenReturn(defaultPrincipalClaim);
        }
        AuthUser expectedUser = new AuthUser("user-1", "Test", "test@example.com", AuthProvider.EXTERNAL, Role.USER);
        when(loadUserPort.findByEmail("test@example.com")).thenReturn(Optional.of(expectedUser));

        AuthUser result = service.resolveUser(jwt);

        assertEquals(expectedUser, result);
    }

    static Stream<Arguments> trustedIssuerPrincipalClaims() {
        return Stream.of(
                Arguments.of("email", null, Map.of("email", "test@example.com")),
                Arguments.of(null, "email", Map.of("email", "test@example.com")),
                Arguments.of("email", null, Map.of("email", "", "sub", "test@example.com")),
                Arguments.of("email", null, Map.of("sub", "test@example.com"))
        );
    }

    @Test
    void resolveUser_throwsWhenNoPrincipalFound() {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("email", "");
        claims.put("sub", "");
        Jwt jwt = jwtWithClaims("https://trusted.example.com", claims);
        TrustedIssuer issuer = new TrustedIssuer();
        issuer.setIssuer("https://trusted.example.com");
        issuer.setPrincipalClaim("email");
        when(securityProperties.resolveByUri(any(URL.class))).thenReturn(Optional.of(issuer));

        assertThrows(JwtException.class, () -> service.resolveUser(jwt));
    }

    @Test
    void resolveUser_handlesJwtWithNoIssuer() {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("email", "test@example.com");
        Jwt jwt = jwtWithClaims(null, claims);
        when(securityProperties.resolveByUri(null)).thenReturn(Optional.empty());
        when(securityProperties.getDefaultPrincipalClaim()).thenReturn("email");
        AuthUser expectedUser = new AuthUser("user-1", "Test", "test@example.com", AuthProvider.EXTERNAL, Role.USER);
        when(loadUserPort.findByEmail("test@example.com")).thenReturn(Optional.of(expectedUser));

        AuthUser result = service.resolveUser(jwt);

        assertEquals(expectedUser, result);
    }

    @Test
    void resolveUser_throwsWhenAudienceNotAllowed() {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("email", "test@example.com");
        claims.put("aud", List.of("wrong-aud"));
        Jwt jwt = jwtWithClaims("https://trusted.example.com", claims);
        TrustedIssuer issuer = new TrustedIssuer();
        issuer.setIssuer("https://trusted.example.com");
        issuer.setPrincipalClaim("email");
        issuer.setAudience(List.of("required-aud"));
        when(securityProperties.resolveByUri(any(URL.class))).thenReturn(Optional.of(issuer));

        assertThrows(JwtException.class, () -> service.resolveUser(jwt));
    }

    @Test
    void resolveUser_throwsWhenProviderMismatch() {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("email", "test@example.com");
        Jwt jwt = jwtWithClaims("https://trusted.example.com", claims);
        TrustedIssuer issuer = new TrustedIssuer();
        issuer.setIssuer("https://trusted.example.com");
        issuer.setPrincipalClaim("email");
        issuer.setProvider(AuthProvider.GOOGLE);
        when(securityProperties.resolveByUri(any(URL.class))).thenReturn(Optional.of(issuer));
        AuthUser existingUser = new AuthUser("user-1", "Test", "test@example.com", AuthProvider.LOCAL, Role.USER);
        when(loadUserPort.findByEmail("test@example.com")).thenReturn(Optional.of(existingUser));

        assertThrows(JwtException.class, () -> service.resolveUser(jwt));
    }

    @Test
    void resolveUser_throwsWhenUserNotRegisteredAndAutoProvisionDisabled() {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("email", "test@example.com");
        Jwt jwt = jwtWithClaims("https://trusted.example.com", claims);
        TrustedIssuer issuer = new TrustedIssuer();
        issuer.setIssuer("https://trusted.example.com");
        issuer.setPrincipalClaim("email");
        issuer.setAutoProvisionUser(false);
        when(securityProperties.resolveByUri(any(URL.class))).thenReturn(Optional.of(issuer));
        when(loadUserPort.findByEmail("test@example.com")).thenReturn(Optional.empty());

        assertThrows(JwtException.class, () -> service.resolveUser(jwt));
    }

    @Test
    void resolveUser_throwsWhenUserNotRegisteredAndNoIssuerConfig() {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("email", "test@example.com");
        Jwt jwt = jwtWithClaims("https://unknown.example.com", claims);
        when(securityProperties.resolveByUri(any(URL.class))).thenReturn(Optional.empty());
        when(securityProperties.getDefaultPrincipalClaim()).thenReturn("email");
        when(loadUserPort.findByEmail("test@example.com")).thenReturn(Optional.empty());

        assertThrows(JwtException.class, () -> service.resolveUser(jwt));
    }

    @Test
    void resolveUser_autoProvisionsNewUser() {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("email", "test@example.com");
        claims.put("name", "Test User");
        Jwt jwt = jwtWithClaims("https://trusted.example.com", claims);
        TrustedIssuer issuer = new TrustedIssuer();
        issuer.setIssuer("https://trusted.example.com");
        issuer.setPrincipalClaim("email");
        issuer.setNameClaim("name");
        issuer.setAutoProvisionUser(true);
        when(securityProperties.resolveByUri(any(URL.class))).thenReturn(Optional.of(issuer));
        when(loadUserPort.findByEmail("test@example.com")).thenReturn(Optional.empty());
        AuthUser newUser = new AuthUser("new-user", "Test User", "test@example.com", AuthProvider.EXTERNAL, Role.USER);
        when(userCommandPort.registerSocialUser("Test User", "test@example.com", AuthProvider.EXTERNAL, Role.USER))
                .thenReturn(newUser);

        AuthUser result = service.resolveUser(jwt);

        assertEquals(newUser, result);
    }

    @Test
    void resolveUser_autoProvisionsWithDefaultRole() {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("email", "test@example.com");
        claims.put("name", "Test User");
        Jwt jwt = jwtWithClaims("https://trusted.example.com", claims);
        TrustedIssuer issuer = new TrustedIssuer();
        issuer.setIssuer("https://trusted.example.com");
        issuer.setPrincipalClaim("email");
        issuer.setNameClaim("name");
        issuer.setAutoProvisionUser(true);
        issuer.setDefaultRole(Role.ADMIN);
        when(securityProperties.resolveByUri(any(URL.class))).thenReturn(Optional.of(issuer));
        when(loadUserPort.findByEmail("test@example.com")).thenReturn(Optional.empty());
        AuthUser newUser = new AuthUser("new-user", "Test User", "test@example.com", AuthProvider.EXTERNAL, Role.ADMIN);
        when(userCommandPort.registerSocialUser("Test User", "test@example.com", AuthProvider.EXTERNAL, Role.ADMIN))
                .thenReturn(newUser);

        AuthUser result = service.resolveUser(jwt);

        assertEquals(Role.ADMIN, result.role());
    }

    @Test
    void resolveUser_autoProvisionsWithAutoCreateAccount() {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("email", "test@example.com");
        claims.put("name", "Test User");
        Jwt jwt = jwtWithClaims("https://trusted.example.com", claims);
        TrustedIssuer issuer = new TrustedIssuer();
        issuer.setIssuer("https://trusted.example.com");
        issuer.setPrincipalClaim("email");
        issuer.setNameClaim("name");
        issuer.setAutoProvisionUser(true);
        issuer.setAutoProvisionAccount(true);
        when(securityProperties.resolveByUri(any(URL.class))).thenReturn(Optional.of(issuer));
        when(loadUserPort.findByEmail("test@example.com")).thenReturn(Optional.empty());
        AuthUser newUser = new AuthUser("new-user", "Test User", "test@example.com", AuthProvider.EXTERNAL, Role.USER);
        when(userCommandPort.registerSocialUser("Test User", "test@example.com", AuthProvider.EXTERNAL, Role.USER))
                .thenReturn(newUser);

        AuthUser result = service.resolveUser(jwt);

        assertEquals(newUser, result);
        verify(accountsPort).createAccount(any(), eq("new-user"));
    }

    @Test
    void resolveUser_autoProvisionsWithoutAutoCreateAccount() {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("email", "test@example.com");
        claims.put("name", "Test User");
        Jwt jwt = jwtWithClaims("https://trusted.example.com", claims);
        TrustedIssuer issuer = new TrustedIssuer();
        issuer.setIssuer("https://trusted.example.com");
        issuer.setPrincipalClaim("email");
        issuer.setNameClaim("name");
        issuer.setAutoProvisionUser(true);
        issuer.setAutoProvisionAccount(false);
        when(securityProperties.resolveByUri(any(URL.class))).thenReturn(Optional.of(issuer));
        when(loadUserPort.findByEmail("test@example.com")).thenReturn(Optional.empty());
        AuthUser newUser = new AuthUser("new-user", "Test User", "test@example.com", AuthProvider.EXTERNAL, Role.USER);
        when(userCommandPort.registerSocialUser("Test User", "test@example.com", AuthProvider.EXTERNAL, Role.USER))
                .thenReturn(newUser);

        AuthUser result = service.resolveUser(jwt);

        assertEquals(newUser, result);
        verify(accountsPort, never()).createAccount(any(), any());
    }

    @Test
    void resolveUser_autoProvisionsUsesPrincipalAsDisplayNameWhenNameClaimEmpty() {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("email", "test@example.com");
        claims.put("name", "");
        Jwt jwt = jwtWithClaims("https://trusted.example.com", claims);
        TrustedIssuer issuer = new TrustedIssuer();
        issuer.setIssuer("https://trusted.example.com");
        issuer.setPrincipalClaim("email");
        issuer.setNameClaim("name");
        issuer.setAutoProvisionUser(true);
        when(securityProperties.resolveByUri(any(URL.class))).thenReturn(Optional.of(issuer));
        when(loadUserPort.findByEmail("test@example.com")).thenReturn(Optional.empty());
        AuthUser newUser = new AuthUser("new-user", "test@example.com", "test@example.com", AuthProvider.EXTERNAL, Role.USER);
        when(userCommandPort.registerSocialUser("test@example.com", "test@example.com", AuthProvider.EXTERNAL, Role.USER))
                .thenReturn(newUser);

        AuthUser result = service.resolveUser(jwt);

        assertEquals(newUser, result);
    }

    @Test
    void resolveUser_normalizesPrincipalToLowercaseAndTrimmed() {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("email", "  Test@Example.com  ");
        Jwt jwt = jwtWithClaims("https://trusted.example.com", claims);
        TrustedIssuer issuer = new TrustedIssuer();
        issuer.setIssuer("https://trusted.example.com");
        issuer.setPrincipalClaim("email");
        when(securityProperties.resolveByUri(any(URL.class))).thenReturn(Optional.of(issuer));
        AuthUser expectedUser = new AuthUser("user-1", "Test", "test@example.com", AuthProvider.EXTERNAL, Role.USER);
        when(loadUserPort.findByEmail("test@example.com")).thenReturn(Optional.of(expectedUser));

        AuthUser result = service.resolveUser(jwt);

        assertEquals(expectedUser, result);
    }

    @Test
    void resolveUser_usesDefaultProviderWhenIssuerProviderIsNull() {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("email", "test@example.com");
        claims.put("name", "Test User");
        Jwt jwt = jwtWithClaims("https://trusted.example.com", claims);
        TrustedIssuer issuer = new TrustedIssuer();
        issuer.setIssuer("https://trusted.example.com");
        issuer.setPrincipalClaim("email");
        issuer.setNameClaim("name");
        issuer.setAutoProvisionUser(true);
        issuer.setProvider(null);
        when(securityProperties.resolveByUri(any(URL.class))).thenReturn(Optional.of(issuer));
        when(loadUserPort.findByEmail("test@example.com")).thenReturn(Optional.empty());
        AuthUser newUser = new AuthUser("new-user", "Test User", "test@example.com", AuthProvider.EXTERNAL, Role.USER);
        when(userCommandPort.registerSocialUser("Test User", "test@example.com", AuthProvider.EXTERNAL, Role.USER))
                .thenReturn(newUser);

        AuthUser result = service.resolveUser(jwt);

        assertEquals(newUser, result);
    }

    @Test
    void resolveUser_usesDefaultRoleWhenIssuerDefaultRoleIsNull() {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("email", "test@example.com");
        claims.put("name", "Test User");
        Jwt jwt = jwtWithClaims("https://trusted.example.com", claims);
        TrustedIssuer issuer = new TrustedIssuer();
        issuer.setIssuer("https://trusted.example.com");
        issuer.setPrincipalClaim("email");
        issuer.setNameClaim("name");
        issuer.setAutoProvisionUser(true);
        issuer.setDefaultRole(null);
        when(securityProperties.resolveByUri(any(URL.class))).thenReturn(Optional.of(issuer));
        when(loadUserPort.findByEmail("test@example.com")).thenReturn(Optional.empty());
        AuthUser newUser = new AuthUser("new-user", "Test User", "test@example.com", AuthProvider.EXTERNAL, Role.USER);
        when(userCommandPort.registerSocialUser("Test User", "test@example.com", AuthProvider.EXTERNAL, Role.USER))
                .thenReturn(newUser);

        AuthUser result = service.resolveUser(jwt);

        assertEquals(Role.USER, result.role());
    }
}
