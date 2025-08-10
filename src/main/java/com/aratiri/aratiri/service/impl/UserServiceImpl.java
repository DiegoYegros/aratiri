package com.aratiri.aratiri.service.impl;

import com.aratiri.aratiri.entity.UserEntity;
import com.aratiri.aratiri.enums.AuthProvider;
import com.aratiri.aratiri.repository.UserRepository;
import com.aratiri.aratiri.service.UserService;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void register(String name, String email, String encodedPassowrd) {
        UserEntity user = new UserEntity();
        user.setName(name);
        user.setEmail(email);
        user.setPassword(encodedPassowrd);
        user.setAuthProvider(AuthProvider.LOCAL);
        userRepository.save(user);
    }
}