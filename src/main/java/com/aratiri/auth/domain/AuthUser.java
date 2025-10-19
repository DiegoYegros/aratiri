package com.aratiri.auth.domain;

import com.aratiri.enums.AuthProvider;

public record AuthUser(String id, String name, String email, AuthProvider provider) {
}
