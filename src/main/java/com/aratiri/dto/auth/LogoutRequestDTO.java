package com.aratiri.dto.auth;

import lombok.Data;

@Data
public class LogoutRequestDTO {
    private String refreshToken;
}
