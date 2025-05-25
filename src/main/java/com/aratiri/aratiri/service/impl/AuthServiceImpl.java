package com.aratiri.aratiri.service.impl;

import com.aratiri.aratiri.dto.users.UserDTO;
import com.aratiri.aratiri.entity.UserEntity;
import com.aratiri.aratiri.exception.AratiriException;
import com.aratiri.aratiri.repository.UserRepository;
import com.aratiri.aratiri.service.AuthService;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;

    public AuthServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDTO getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        UserEntity userEntity = userRepository.findByEmail(email)
                .orElseThrow(() -> new AratiriException("User not found"));
        return new UserDTO(userEntity.getId(), userEntity.getName(), userEntity.getEmail());
    }
}