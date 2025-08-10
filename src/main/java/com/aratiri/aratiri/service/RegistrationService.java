package com.aratiri.aratiri.service;

import com.aratiri.aratiri.dto.auth.AuthResponseDTO;
import com.aratiri.aratiri.dto.auth.RegistrationRequestDTO;
import com.aratiri.aratiri.dto.auth.VerificationRequestDTO;

public interface RegistrationService {
    void initiateRegistration(RegistrationRequestDTO request);
    AuthResponseDTO completeRegistration(VerificationRequestDTO request);
}