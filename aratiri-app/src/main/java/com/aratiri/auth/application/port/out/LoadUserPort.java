package com.aratiri.auth.application.port.out;

import com.aratiri.auth.domain.AuthUser;

import java.util.Optional;

public interface LoadUserPort {

    Optional<AuthUser> findByEmail(String email);

    Optional<AuthUser> findById(String id);
}
