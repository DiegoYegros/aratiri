package com.aratiri.webhooks.application.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Data
@Builder
public class WebhookEndpointResponseDTO {

    private UUID id;
    private String name;
    private String url;
    private Set<String> eventTypes;
    private Boolean enabled;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant lastSuccessAt;
    private Instant lastFailureAt;
}
