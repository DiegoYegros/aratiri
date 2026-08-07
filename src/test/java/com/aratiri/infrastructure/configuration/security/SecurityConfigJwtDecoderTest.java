package com.aratiri.infrastructure.configuration.security;

import com.aratiri.auth.infrastructure.security.ChainedJwtDecoder;
import com.aratiri.infrastructure.configuration.AratiriProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SecurityConfigJwtDecoderTest {

    private final SecurityConfig securityConfig = new SecurityConfig(null, null);

    @Test
    void applicationYml_defaultsTrustedIssuersToEmptyList() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("application.yml")) {
            assertNotNull(in, "application.yml must be on the test classpath");
            Map<String, Object> root = new Yaml().load(in);
            @SuppressWarnings("unchecked")
            Map<String, Object> security = (Map<String, Object>) ((Map<String, Object>) root.get("aratiri")).get("security");
            Object trustedIssuers = security.get("trusted-issuers");
            assertInstanceOf(List.class, trustedIssuers);
            assertTrue(((List<?>) trustedIssuers).isEmpty(),
                    "Production default must not register a localhost JWKS trusted issuer");
        }
    }

    @Test
    void jwtDecoder_emptyTrustedIssuers_registersOnlyLocalDecoder() throws Exception {
        AratiriProperties aratiriProperties = aratiriPropertiesWithSecret("unit-test-jwt-secret-value-32b");
        AratiriSecurityProperties securityProperties = new AratiriSecurityProperties();
        assertTrue(securityProperties.getTrustedIssuers().isEmpty());

        JwtDecoder decoder = securityConfig.jwtDecoder(aratiriProperties, securityProperties);

        assertInstanceOf(ChainedJwtDecoder.class, decoder);
        assertEquals(1, delegateCount((ChainedJwtDecoder) decoder));
        assertThrows(JwtException.class, () -> decoder.decode(
                "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.e30.e30"));
    }

    @Test
    void jwtDecoder_nullTrustedIssuers_registersOnlyLocalDecoder() throws Exception {
        AratiriProperties aratiriProperties = aratiriPropertiesWithSecret("unit-test-jwt-secret-value-32b");
        AratiriSecurityProperties securityProperties = new AratiriSecurityProperties();
        securityProperties.setTrustedIssuers(null);

        JwtDecoder decoder = securityConfig.jwtDecoder(aratiriProperties, securityProperties);

        assertEquals(1, delegateCount((ChainedJwtDecoder) decoder));
    }

    @Test
    void jwtDecoder_issuerWithoutJwksOrIssuerUri_isSkipped() throws Exception {
        AratiriProperties aratiriProperties = aratiriPropertiesWithSecret("unit-test-jwt-secret-value-32b");
        AratiriSecurityProperties securityProperties = new AratiriSecurityProperties();
        AratiriSecurityProperties.TrustedIssuer incomplete = new AratiriSecurityProperties.TrustedIssuer();
        incomplete.setIssuer("http://localhost:8000");
        securityProperties.setTrustedIssuers(List.of(incomplete));

        JwtDecoder decoder = securityConfig.jwtDecoder(aratiriProperties, securityProperties);

        assertEquals(1, delegateCount((ChainedJwtDecoder) decoder));
    }

    @Test
    void jwtDecoder_explicitTrustedIssuer_registersJwksDecoder() throws Exception {
        AratiriProperties aratiriProperties = aratiriPropertiesWithSecret("unit-test-jwt-secret-value-32b");
        AratiriSecurityProperties securityProperties = new AratiriSecurityProperties();
        AratiriSecurityProperties.TrustedIssuer issuer = new AratiriSecurityProperties.TrustedIssuer();
        issuer.setIssuer("http://localhost:8000");
        issuer.setJwkSetUri("http://localhost:8000/jwks.json");
        securityProperties.setTrustedIssuers(List.of(issuer));

        JwtDecoder decoder = securityConfig.jwtDecoder(aratiriProperties, securityProperties);

        assertEquals(2, delegateCount((ChainedJwtDecoder) decoder));
    }

    private static AratiriProperties aratiriPropertiesWithSecret(String secret) {
        AratiriProperties properties = new AratiriProperties();
        properties.setJwtSecret(secret);
        return properties;
    }

    @SuppressWarnings("unchecked")
    private static int delegateCount(ChainedJwtDecoder decoder) throws Exception {
        Field delegates = ChainedJwtDecoder.class.getDeclaredField("delegates");
        delegates.setAccessible(true);
        return ((List<JwtDecoder>) delegates.get(decoder)).size();
    }
}
