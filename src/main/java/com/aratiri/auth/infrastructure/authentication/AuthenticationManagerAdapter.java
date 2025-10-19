package com.aratiri.auth.infrastructure.authentication;

import com.aratiri.auth.application.port.out.AuthenticationPort;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class AuthenticationManagerAdapter implements AuthenticationPort {

    private final AuthenticationManager authenticationManager;

    public AuthenticationManagerAdapter(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    @Override
    public void authenticate(String username, String password) {
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
    }
}
