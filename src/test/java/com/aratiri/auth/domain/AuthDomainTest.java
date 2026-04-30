package com.aratiri.auth.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuthDomainTest {

    @Test
    void authUser_record_allFields() {
        AuthUser user = new AuthUser("user-1", "Test User", "test@example.com", AuthProvider.LOCAL, Role.ADMIN);
        assertEquals("user-1", user.id());
        assertEquals("Test User", user.name());
        assertEquals("test@example.com", user.email());
        assertEquals(AuthProvider.LOCAL, user.provider());
        assertEquals(Role.ADMIN, user.role());
    }

    @Test
    void authUser_withGoogleProvider() {
        AuthUser user = new AuthUser("u-2", "G User", "g@example.com", AuthProvider.GOOGLE, Role.USER);
        assertEquals(AuthProvider.GOOGLE, user.provider());
        assertEquals(Role.USER, user.role());
    }

    @Test
    void role_enumValues() {
        assertEquals(4, Role.values().length);
        assertNotNull(Role.valueOf("USER"));
        assertNotNull(Role.valueOf("ADMIN"));
        assertNotNull(Role.valueOf("SUPERADMIN"));
        assertNotNull(Role.valueOf("VIEWER"));
    }

    @Test
    void authProvider_enumValues() {
        assertEquals(3, AuthProvider.values().length);
        assertNotNull(AuthProvider.valueOf("LOCAL"));
        assertNotNull(AuthProvider.valueOf("GOOGLE"));
        assertNotNull(AuthProvider.valueOf("EXTERNAL"));
    }

    @Test
    void googleUserProfile_record() {
        GoogleUserProfile profile = new GoogleUserProfile("test@example.com", "Test User");
        assertEquals("test@example.com", profile.email());
        assertEquals("Test User", profile.name());
    }

    @Test
    void googleUserProfile_withNullName() {
        GoogleUserProfile profile = new GoogleUserProfile("test@example.com", null);
        assertEquals("test@example.com", profile.email());
        assertNull(profile.name());
    }
}
