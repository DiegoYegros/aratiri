package com.aratiri.auth.application.port.in;

public record PasswordResetCompletionCommand(String email, String code, String newPassword) {
}
