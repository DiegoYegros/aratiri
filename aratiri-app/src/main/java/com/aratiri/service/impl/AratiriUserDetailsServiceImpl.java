package com.aratiri.service.impl;

import com.aratiri.entity.UserEntity;
import com.aratiri.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.UUID;

@Service
public class AratiriUserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    public AratiriUserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

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
        var authorities = Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_" + user.getRole().name())
        );
        return new org.springframework.security.core.userdetails.User(
                user.getEmail(), passwd, authorities);
    }
}

