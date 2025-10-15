package com.aratiri.service;

import com.aratiri.dto.auth.AuthResponseDTO;

public interface GoogleSsoService {
    AuthResponseDTO loginWithGoogle(String googleToken);
}
