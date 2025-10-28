package com.aratiri.auth.application.dto;

import jakarta.validation.constraints.NotBlank;

public class TokenExchangeRequestDTO {

    @NotBlank
    private String externalToken;

    public String getExternalToken() {
        return externalToken;
    }

    public void setExternalToken(String externalToken) {
        this.externalToken = externalToken;
    }
}
