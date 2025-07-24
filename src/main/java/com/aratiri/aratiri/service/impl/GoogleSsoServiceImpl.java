package com.aratiri.aratiri.service.impl;

import com.aratiri.aratiri.entity.UserEntity;
import com.aratiri.aratiri.exception.AratiriException;
import com.aratiri.aratiri.repository.UserRepository;
import com.aratiri.aratiri.service.GoogleSsoService;
import com.aratiri.aratiri.utils.JwtUtil;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

@Service
public class GoogleSsoServiceImpl implements GoogleSsoService {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final GoogleIdTokenVerifier verifier;

    public GoogleSsoServiceImpl(UserRepository userRepository, JwtUtil jwtUtil, @Value("${spring.security.oauth2.client.registration.google.client-id}") String googleClientId) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                .setAudience(Collections.singletonList(googleClientId))
                .build();
    }

    @Transactional
    public String loginWithGoogle(String googleToken) {
        try {
            GoogleIdToken idToken = verifier.verify(googleToken);
            if (idToken == null) {
                throw new AratiriException("Google Token Invalid", HttpStatus.UNAUTHORIZED);
            }
            GoogleIdToken.Payload payload = idToken.getPayload();
            String email = payload.getEmail();
            String name = (String) payload.get("name");
            UserEntity user = userRepository.findByEmail(email)
                    .orElseGet(() -> {
                        UserEntity newUser = new UserEntity();
                        newUser.setEmail(email);
                        newUser.setName(name);
                        newUser.setPassword(null);
                        return userRepository.save(newUser);
                    });
            return jwtUtil.generateToken(user.getEmail());
        } catch (Exception e) {
            logger.error("Auth failed with Google, message is: {}", e.getMessage(), e);
            throw new AratiriException("Auth Failed with Google", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}