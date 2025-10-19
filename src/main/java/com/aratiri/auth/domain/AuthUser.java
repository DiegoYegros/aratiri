package com.aratiri.auth.domain;

import com.aratiri.enums.AuthProvider;
import com.aratiri.enums.Role;

public record AuthUser(String id, String name, String email, AuthProvider provider, Role role) {
}
