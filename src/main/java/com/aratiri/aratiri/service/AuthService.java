package com.aratiri.aratiri.service;

import com.aratiri.aratiri.dto.users.UserDTO;

public interface AuthService {
    UserDTO getCurrentUser();
}