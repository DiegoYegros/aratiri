package com.aratiri.auth.domain;

public record AuthUser(String id, String name, String email, AuthProvider provider, Role role) {
}
