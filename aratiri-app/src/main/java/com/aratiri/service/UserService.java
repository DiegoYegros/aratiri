package com.aratiri.service;

import com.aratiri.entity.UserEntity;

import java.util.Optional;

public interface UserService {
    UserEntity register(String name, String email, String password);

    void updatePassword(UserEntity user, String newPassword);

    Optional<UserEntity> findByEmail(String email);
}
