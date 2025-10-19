package com.aratiri.auth.api.dto;

import lombok.Data;

@Data
public class RefreshTokenRequestDTO {
    private String refreshToken;
}