package com.aratiri.service.impl;

import com.aratiri.entity.UserEntity;
import com.aratiri.enums.AuthProvider;
import com.aratiri.repository.UserRepository;
import com.aratiri.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    @Override
    public UserEntity register(String name, String email, String encodedPassowrd) {
        UserEntity user = new UserEntity();
        user.setName(name);
        user.setEmail(email);
        user.setPassword(encodedPassowrd);
        user.setAuthProvider(AuthProvider.LOCAL);
        return userRepository.save(user);
    }

    @Override
    public void updatePassword(UserEntity user, String encodedPassword) {
        user.setPassword(encodedPassword);
        userRepository.save(user);
    }

    @Override
    public Optional<UserEntity> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

}