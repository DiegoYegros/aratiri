package com.aratiri.webhooks.application.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class WebhookDeliveryResponseDTO {

    private UUID id;
    private UUID eventId;
    private UUID endpointId;
    private String status;
    private Integer attemptCount;
    private Instant nextAttemptAt;
    private Integer responseStatus;
    private String lastError;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant deliveredAt;
    private String eventType;
}
