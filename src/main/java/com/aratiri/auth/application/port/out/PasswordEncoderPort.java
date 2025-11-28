package com.aratiri.auth.application.port.out;

public interface PasswordEncoderPort {

    String encode(String rawPassword);
}
