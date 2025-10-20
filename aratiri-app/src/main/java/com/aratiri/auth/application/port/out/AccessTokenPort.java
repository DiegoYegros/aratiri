package com.aratiri.auth.application.port.out;

public interface AccessTokenPort {

    String generateAccessToken(String username);
}
