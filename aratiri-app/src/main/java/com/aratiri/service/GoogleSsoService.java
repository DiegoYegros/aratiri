package com.aratiri.service;

import com.aratiri.auth.api.dto.AuthResponseDTO;

public interface GoogleSsoService {
    AuthResponseDTO loginWithGoogle(String googleToken);
}
