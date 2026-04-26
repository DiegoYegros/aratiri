package com.aratiri.webhooks.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Set;

@Data
public class UpdateWebhookEndpointRequestDTO {

    @NotBlank
    private String name;

    @NotBlank
    private String url;

    @NotEmpty
    private Set<String> eventTypes;

    @NotNull
    private Boolean enabled;
}
