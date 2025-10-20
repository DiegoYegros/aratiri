package com.aratiri.auth.application.dto;

import lombok.Data;

@Data
public class LogoutRequestDTO {
    private String refreshToken;
}
