package com.aratiri.auth.api.dto;

import lombok.Data;

@Data
public class AuthRequestDTO {
    private String username;
    private String password;
}