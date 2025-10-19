package com.aratiri.auth.infrastructure.persistence;

import com.aratiri.auth.application.port.out.RegistrationDraftPort;
import com.aratiri.auth.domain.RegistrationDraft;
import com.aratiri.entity.VerificationData;
import com.aratiri.repository.VerificationDataRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class VerificationDataRepositoryAdapter implements RegistrationDraftPort {

    private final VerificationDataRepository verificationDataRepository;

    public VerificationDataRepositoryAdapter(VerificationDataRepository verificationDataRepository) {
        this.verificationDataRepository = verificationDataRepository;
    }

    @Override
    public void save(RegistrationDraft draft) {
        VerificationData verificationData = VerificationData.builder()
                .email(draft.email())
                .name(draft.name())
                .password(draft.encodedPassword())
                .alias(draft.alias())
                .code(draft.code())
                .expiresAt(draft.expiresAt())
                .build();
        verificationDataRepository.save(verificationData);
    }

    @Override
    public Optional<RegistrationDraft> findByEmail(String email) {
        return verificationDataRepository.findById(email).map(this::toDomain);
    }

    @Override
    public void deleteByEmail(String email) {
        verificationDataRepository.deleteById(email);
    }

    private RegistrationDraft toDomain(VerificationData verificationData) {
        return new RegistrationDraft(
                verificationData.getEmail(),
                verificationData.getName(),
                verificationData.getPassword(),
                verificationData.getAlias(),
                verificationData.getCode(),
                verificationData.getExpiresAt()
        );
    }
}
