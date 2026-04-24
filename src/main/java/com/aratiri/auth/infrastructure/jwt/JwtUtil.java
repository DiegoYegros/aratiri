package com.aratiri.auth.infrastructure.jwt;

import com.aratiri.infrastructure.configuration.AratiriProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtUtil {

    private final AratiriProperties aratiriProperties;

    public JwtUtil(AratiriProperties aratiriProperties) {
        this.aratiriProperties = aratiriProperties;
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(aratiriProperties.getJwtSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String username) {
        return generateTokenWithExpiration(username, aratiriProperties.getJwtExpiration());
    }

    public String generateTokenFromUsername(String username) {
        return generateToken(username);
    }

    private String generateTokenWithExpiration(String username, long expirationInSeconds) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationInSeconds)))
                .signWith(signingKey())
                .compact();
    }

    public String extractUsername(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    public boolean isTokenValid(String token, String username) {
        return extractUsername(token).equals(username) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        Date expiration = Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getExpiration();
        return expiration.before(new Date());
    }
}
