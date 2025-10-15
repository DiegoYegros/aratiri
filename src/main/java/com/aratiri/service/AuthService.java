package com.aratiri.service;

import com.aratiri.dto.auth.AuthRequestDTO;
import com.aratiri.dto.auth.AuthResponseDTO;
import com.aratiri.dto.users.UserDTO;

public interface AuthService {
    UserDTO getCurrentUser();

    AuthResponseDTO login(AuthRequestDTO request);

    void logout(String refreshToken);

}