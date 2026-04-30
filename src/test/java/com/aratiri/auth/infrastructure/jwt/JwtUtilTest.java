package com.aratiri.auth.infrastructure.jwt;

import com.aratiri.infrastructure.configuration.AratiriProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtUtilTest {

    @Mock
    private AratiriProperties aratiriProperties;

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        // Use a proper secret key for HS256 (at least 256 bits / 32 bytes)
        when(aratiriProperties.getJwtSecret())
                .thenReturn("this-is-a-secret-key-that-is-long-enough-for-hs256!!");
        when(aratiriProperties.getJwtExpiration()).thenReturn(3600L);
        jwtUtil = new JwtUtil(aratiriProperties);
    }

    @Test
    void generateToken_createsValidToken() {
        String token = jwtUtil.generateToken("user@test.com");

        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    void generateTokenFromUsername_delegatesToGenerateToken() {
        String token = jwtUtil.generateTokenFromUsername("user@test.com");

        assertNotNull(token);
    }

    @Test
    void extractUsername_returnsCorrectUsername() {
        String token = jwtUtil.generateToken("user@test.com");

        String username = jwtUtil.extractUsername(token);

        assertEquals("user@test.com", username);
    }

    @Test
    void isTokenValid_returnsTrueForValidToken() {
        String token = jwtUtil.generateToken("user@test.com");

        assertTrue(jwtUtil.isTokenValid(token, "user@test.com"));
    }

    @Test
    void isTokenValid_returnsFalseForWrongUsername() {
        String token = jwtUtil.generateToken("user@test.com");

        assertFalse(jwtUtil.isTokenValid(token, "other@test.com"));
    }
}
