package com.aratiri.dto.accounts;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateAccountRequestDTO {
    @NotNull(message = "userId no puede ser nulo.")
    private String userId;
    private String alias;
}