package com.aratiri.aratiri.service.impl;

import com.aratiri.aratiri.dto.auth.AuthRequestDTO;
import com.aratiri.aratiri.dto.auth.AuthResponseDTO;
import com.aratiri.aratiri.dto.users.UserDTO;
import com.aratiri.aratiri.entity.UserEntity;
import com.aratiri.aratiri.enums.AuthProvider;
import com.aratiri.aratiri.exception.AratiriException;
import com.aratiri.aratiri.repository.UserRepository;
import com.aratiri.aratiri.service.AuthService;
import com.aratiri.aratiri.utils.JwtUtil;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authManager;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    public AuthServiceImpl(AuthenticationManager authManager, JwtUtil jwtUtil, UserRepository userRepository) {
        this.authManager = authManager;
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
    }

    @Override
    public UserDTO getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        UserEntity userEntity = userRepository.findByEmail(email)
                .orElseThrow(() -> new AratiriException("User not found"));
        return new UserDTO(userEntity.getId(), userEntity.getName(), userEntity.getEmail());
    }

    @Override
    public AuthResponseDTO login(AuthRequestDTO request) {
        UserEntity user = userRepository.findByEmail(request.getUsername())
                .orElseThrow(() -> new AratiriException("Invalid username or password"));
        if (user.getAuthProvider() == AuthProvider.GOOGLE) {
            throw new AratiriException("Please log in using your Google account.");
        }
        authManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
        String token = jwtUtil.generateToken(request.getUsername());
        return new AuthResponseDTO(token);
    }
}