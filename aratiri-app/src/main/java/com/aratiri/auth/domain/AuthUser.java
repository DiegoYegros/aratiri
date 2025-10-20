package com.aratiri.auth.domain;

import com.aratiri.auth.domain.AuthProvider;
import com.aratiri.auth.domain.Role;

public record AuthUser(String id, String name, String email, AuthProvider provider, Role role) {
}
