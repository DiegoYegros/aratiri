package com.aratiri.infrastructure.persistence.jpa.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class WebhookDeliveryEntityTest {

    @Test
    void builder_createsEntity() {
        var now = Instant.now();
        WebhookDeliveryEntity entity = WebhookDeliveryEntity.builder()
                .eventId(java.util.UUID.randomUUID())
                .endpointId(java.util.UUID.randomUUID())
                .eventType("invoice.created")
                .payload("{}")
                .status(WebhookDeliveryStatus.PENDING)
                .attemptCount(0)
                .nextAttemptAt(now)
                .build();

        assertEquals("invoice.created", entity.getEventType());
        assertEquals("{}", entity.getPayload());
        assertEquals(WebhookDeliveryStatus.PENDING, entity.getStatus());
        assertEquals(0, entity.getAttemptCount());
    }

    @Test
    void prePersist_setsDefaultStatusWhenNull() {
        WebhookDeliveryEntity entity = new WebhookDeliveryEntity();
        entity.prePersist();

        assertEquals(WebhookDeliveryStatus.PENDING, entity.getStatus());
        assertEquals(0, entity.getAttemptCount());
        assertNotNull(entity.getNextAttemptAt());
    }

    @Test
    void prePersist_doesNotOverrideExistingValues() {
        WebhookDeliveryEntity entity = new WebhookDeliveryEntity();
        entity.setAttemptCount(3);
        entity.setNextAttemptAt(Instant.EPOCH);
        entity.prePersist();

        assertEquals(3, entity.getAttemptCount());
        assertEquals(Instant.EPOCH, entity.getNextAttemptAt());
    }
}
