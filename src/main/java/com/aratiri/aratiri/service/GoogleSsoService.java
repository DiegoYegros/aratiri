package com.aratiri.aratiri.service;

import com.aratiri.aratiri.dto.auth.AuthResponseDTO;

public interface GoogleSsoService {
    AuthResponseDTO loginWithGoogle(String googleToken);
}
