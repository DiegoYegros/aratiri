package com.aratiri.auth.infrastructure.jwt;

import com.aratiri.auth.application.port.out.AccessTokenPort;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenAdapter implements AccessTokenPort {

    private final JwtUtil jwtUtil;

    public JwtTokenAdapter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public String generateAccessToken(String username) {
        return jwtUtil.generateToken(username);
    }
}
