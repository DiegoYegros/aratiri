package com.aratiri.webhooks.application;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WebhookTestEventFactsTest {

    @Test
    void recordStoresEndpointId() {
        UUID endpointId = UUID.randomUUID();
        WebhookTestEventFacts facts = new WebhookTestEventFacts(endpointId);
        assertEquals(endpointId, facts.endpointId());
    }
}
