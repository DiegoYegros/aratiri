package com.aratiri.aratiri.service;

import com.aratiri.aratiri.entity.UserEntity;
import com.aratiri.aratiri.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.UUID;

@Service
public class AratiriUserDetailsService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        String passwd;
        if (user.getPassword() == null || user.getPassword().isEmpty()) {
            passwd = UUID.randomUUID().toString();
        } else {
            passwd = user.getPassword();
        }
        return new org.springframework.security.core.userdetails.User(
                user.getEmail(), passwd, Collections.emptyList());
    }
}

