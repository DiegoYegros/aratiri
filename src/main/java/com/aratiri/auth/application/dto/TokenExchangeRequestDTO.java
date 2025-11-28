package com.aratiri.auth.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class TokenExchangeRequestDTO {

    @NotBlank
    private String externalToken;

}
