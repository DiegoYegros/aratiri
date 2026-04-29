package com.aratiri.webhooks.application;

import java.util.Objects;
import java.util.UUID;

public record WebhookTestEventFacts(UUID endpointId) {
    public WebhookTestEventFacts {
        Objects.requireNonNull(endpointId, "endpointId must not be null");
    }
}
