package com.aratiri.webhooks.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class WebhookPayload {

    private String id;

    private String type;

    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("api_version")
    private String apiVersion;

    private WebhookPayloadData data;
}
