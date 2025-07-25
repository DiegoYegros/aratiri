package com.aratiri.aratiri.service;

import com.aratiri.aratiri.dto.auth.AuthRequestDTO;
import com.aratiri.aratiri.dto.auth.AuthResponseDTO;
import com.aratiri.aratiri.dto.users.UserDTO;

public interface AuthService {
    UserDTO getCurrentUser();

    AuthResponseDTO login(AuthRequestDTO request);
}