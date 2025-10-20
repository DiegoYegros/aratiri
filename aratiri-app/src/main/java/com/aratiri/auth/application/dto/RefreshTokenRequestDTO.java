package com.aratiri.auth.application.dto;

import lombok.Data;

@Data
public class RefreshTokenRequestDTO {
    private String refreshToken;
}