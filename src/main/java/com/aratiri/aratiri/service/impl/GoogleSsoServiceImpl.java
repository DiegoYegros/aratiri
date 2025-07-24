package com.aratiri.aratiri.service.impl;

import com.aratiri.aratiri.entity.UserEntity;
import com.aratiri.aratiri.enums.AuthProvider;
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
import java.util.Optional;

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

    @Override
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
            Optional<UserEntity> userOptional = userRepository.findByEmail(email);
            UserEntity user;
            if (userOptional.isPresent()) {
                user = userOptional.get();
                if (user.getAuthProvider() != AuthProvider.GOOGLE) {
                    throw new AratiriException("This email is registered with a password. Please use the standard login.", HttpStatus.BAD_REQUEST);
                }
            } else {
                logger.info("Creating new user for email via Google SSO: {}", email);
                UserEntity newUser = new UserEntity();
                newUser.setEmail(email);
                newUser.setName(name);
                newUser.setPassword(null);
                newUser.setAuthProvider(AuthProvider.GOOGLE);
                user = userRepository.save(newUser);
            }

            return jwtUtil.generateToken(user.getEmail());

        } catch (Exception e) {
            logger.error("Auth failed with Google, message is: {}", e.getMessage(), e);
            if (e instanceof AratiriException ex) {
                throw ex;
            }
            throw new AratiriException("Auth Failed with Google", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}