package com.aratiri.auth.infrastructure.jwt;

import com.aratiri.config.AratiriProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class JwtUtil {

    private final AratiriProperties aratiriProperties;

    public JwtUtil(AratiriProperties aratiriProperties) {
        this.aratiriProperties = aratiriProperties;
    }

    public String generateToken(String username) {
        return generateTokenWithExpiration(username, aratiriProperties.getJwtExpiration());
    }

    public String generateTokenFromUsername(String username) {
        return generateToken(username);
    }

    private String generateTokenWithExpiration(String username, long expirationInSeconds) {
        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationInSeconds * 1000))
                .signWith(Keys.hmacShaKeyFor(aratiriProperties.getJwtSecret().getBytes()), SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractUsername(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(aratiriProperties.getJwtSecret().getBytes())
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

    public boolean isTokenValid(String token, String username) {
        return extractUsername(token).equals(username) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(aratiriProperties.getJwtSecret().getBytes())
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getExpiration()
                .before(new Date());
    }
}
