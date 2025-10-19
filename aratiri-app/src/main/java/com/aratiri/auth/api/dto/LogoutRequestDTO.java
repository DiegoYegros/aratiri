package com.aratiri.auth.api.dto;

import lombok.Data;

@Data
public class LogoutRequestDTO {
    private String refreshToken;
}
