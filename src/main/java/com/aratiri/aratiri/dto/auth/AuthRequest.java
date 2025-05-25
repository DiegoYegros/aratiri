package com.aratiri.aratiri.dto.auth;

import lombok.Data;

@Data
public class AuthRequest {
    private String username;
    private String password;
}