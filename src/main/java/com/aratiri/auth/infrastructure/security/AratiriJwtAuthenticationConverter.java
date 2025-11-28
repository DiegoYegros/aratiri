package com.aratiri.auth.infrastructure.security;

import com.aratiri.auth.domain.AuthUser;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AratiriJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final AratiriJwtPrincipalService principalService;

    public AratiriJwtAuthenticationConverter(AratiriJwtPrincipalService principalService) {
        this.principalService = principalService;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt source) {
        AuthUser user = principalService.resolveUser(source);
        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name()));
        return new JwtAuthenticationToken(source, authorities, user.email());
    }
}
