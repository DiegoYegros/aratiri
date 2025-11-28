package com.aratiri.auth.infrastructure.security;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChainedJwtDecoder implements JwtDecoder {

    private final List<JwtDecoder> delegates;

    public ChainedJwtDecoder(List<JwtDecoder> delegates) {
        this.delegates = Collections.unmodifiableList(new ArrayList<>(delegates));
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        JwtException last = null;
        for (JwtDecoder delegate : delegates) {
            try {
                return delegate.decode(token);
            } catch (JwtException ex) {
                last = ex;
            }
        }
        if (last != null) {
            throw last;
        }
        throw new JwtException("No JWT decoders configured");
    }
}
