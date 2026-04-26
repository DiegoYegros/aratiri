package com.aratiri.webhooks.application.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class WebhookSecretResponseDTO {

    private UUID id;
    private String signingSecret;
}
