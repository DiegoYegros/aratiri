package com.aratiri.auth.application.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordResetDTOsTest {

    @Test
    void forgotPasswordRequestDTO() {
        PasswordResetDTOs.ForgotPasswordRequestDTO dto = new PasswordResetDTOs.ForgotPasswordRequestDTO();
        dto.setEmail("test@example.com");
        assertEquals("test@example.com", dto.getEmail());
    }

    @Test
    void resetPasswordRequestDTO() {
        PasswordResetDTOs.ResetPasswordRequestDTO dto = new PasswordResetDTOs.ResetPasswordRequestDTO();
        dto.setEmail("test@example.com");
        dto.setCode("123456");
        dto.setNewPassword("newpass123");
        assertEquals("test@example.com", dto.getEmail());
        assertEquals("123456", dto.getCode());
        assertEquals("newpass123", dto.getNewPassword());
    }
}
