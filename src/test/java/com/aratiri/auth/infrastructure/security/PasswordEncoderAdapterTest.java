package com.aratiri.auth.infrastructure.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordEncoderAdapterTest {

    @Mock
    private PasswordEncoder passwordEncoder;

    private PasswordEncoderAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new PasswordEncoderAdapter(passwordEncoder);
    }

    @Test
    void encode_delegatesToSpringPasswordEncoder() {
        when(passwordEncoder.encode("raw")).thenReturn("hashed");

        String result = adapter.encode("raw");

        assertEquals("hashed", result);
    }
}
