package com.aratiri.auth.application.port.in;

public record VerificationCommand(String email, String code) {
}
