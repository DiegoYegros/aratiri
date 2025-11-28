package com.aratiri.auth.application.port.out;

public interface AuthenticationPort {

    void authenticate(String username, String password);
}
