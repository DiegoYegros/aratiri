package com.aratiri.auth.infrastructure.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SecurityContextAuthenticatedUserAdapterTest {

    private final SecurityContextAuthenticatedUserAdapter adapter =
            new SecurityContextAuthenticatedUserAdapter();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsEmailWhenAuthenticated() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("test@example.com", null));
        SecurityContextHolder.getContext().getAuthentication().setAuthenticated(true);

        Optional<String> result = adapter.getCurrentUserEmail();

        assertTrue(result.isPresent());
        assertEquals("test@example.com", result.get());
    }

    @Test
    void returnsEmptyWhenNotAuthenticated() {
        SecurityContextHolder.getContext().setAuthentication(null);

        Optional<String> result = adapter.getCurrentUserEmail();

        assertTrue(result.isEmpty());
    }

    @Test
    void returnsEmptyWhenExplicitlyNotAuthenticated() {
        TestingAuthenticationToken auth = new TestingAuthenticationToken("test@example.com", null);
        auth.setAuthenticated(false);
        SecurityContextHolder.getContext().setAuthentication(auth);

        Optional<String> result = adapter.getCurrentUserEmail();

        assertTrue(result.isEmpty());
    }
}
