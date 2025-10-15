package com.aratiri.service;

import com.aratiri.dto.auth.PasswordResetDTOs;

public interface PasswordResetService {
    void initiatePasswordReset(PasswordResetDTOs.ForgotPasswordRequestDTO request);

    void completePasswordReset(PasswordResetDTOs.ResetPasswordRequestDTO request);
}