package com.aratiri.service;

import com.aratiri.auth.api.dto.AuthResponseDTO;
import com.aratiri.auth.api.dto.RegistrationRequestDTO;
import com.aratiri.auth.api.dto.VerificationRequestDTO;

public interface RegistrationService {
    void initiateRegistration(RegistrationRequestDTO request);

    AuthResponseDTO completeRegistration(VerificationRequestDTO request);
}