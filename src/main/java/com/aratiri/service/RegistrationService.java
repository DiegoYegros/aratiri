package com.aratiri.service;

import com.aratiri.dto.auth.AuthResponseDTO;
import com.aratiri.dto.auth.RegistrationRequestDTO;
import com.aratiri.dto.auth.VerificationRequestDTO;

public interface RegistrationService {
    void initiateRegistration(RegistrationRequestDTO request);

    AuthResponseDTO completeRegistration(VerificationRequestDTO request);
}