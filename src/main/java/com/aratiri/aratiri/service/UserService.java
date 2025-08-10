package com.aratiri.aratiri.service;

import com.aratiri.aratiri.entity.UserEntity;

public interface UserService {
    UserEntity register(String name, String email, String password);

    void updatePassword(UserEntity user, String newPassword);

}
