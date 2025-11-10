package com.aratiri.auth.application.port.in;

public record RegistrationCommand(String name, String email, String password, String alias) {
}
