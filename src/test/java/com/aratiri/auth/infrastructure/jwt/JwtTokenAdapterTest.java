package com.aratiri.auth.infrastructure.jwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtTokenAdapterTest {

    @Mock
    private JwtUtil jwtUtil;

    private JwtTokenAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new JwtTokenAdapter(jwtUtil);
    }

    @Test
    void generateAccessToken_delegatesToJwtUtil() {
        when(jwtUtil.generateToken("test@example.com")).thenReturn("jwt-token");

        String result = adapter.generateAccessToken("test@example.com");

        assertEquals("jwt-token", result);
    }
}
