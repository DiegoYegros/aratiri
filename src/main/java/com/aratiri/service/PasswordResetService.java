package com.aratiri.service;

import com.aratiri.auth.api.dto.PasswordResetDTOs;

public interface PasswordResetService {
    void initiatePasswordReset(PasswordResetDTOs.ForgotPasswordRequestDTO request);

    void completePasswordReset(PasswordResetDTOs.ResetPasswordRequestDTO request);
}