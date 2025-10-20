package com.aratiri.auth.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

public class PasswordResetDTOs {

    @Data
    public static class ForgotPasswordRequestDTO {
        @Email(message = "Email should be valid")
        @NotBlank(message = "Email cannot be blank")
        private String email;
    }

    @Data
    public static class ResetPasswordRequestDTO {
        @Email(message = "Email should be valid")
        @NotBlank(message = "Email cannot be blank")
        private String email;

        @NotBlank(message = "Code cannot be blank")
        private String code;

        @NotBlank(message = "Password cannot be blank")
        @Size(min = 8, message = "Password must be at least 8 characters long")
        private String newPassword;
    }
}
