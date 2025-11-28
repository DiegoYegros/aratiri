package com.aratiri.auth.application.port.out;

import com.aratiri.auth.domain.RegistrationDraft;

import java.util.Optional;

public interface RegistrationDraftPort {

    void save(RegistrationDraft draft);

    Optional<RegistrationDraft> findByEmail(String email);

    void deleteByEmail(String email);
}
