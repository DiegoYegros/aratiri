package com.aratiri.infrastructure.persistence.jpa.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class OutboxEventEntityTest {

    @Test
    void builder_createsEntity() {
        OutboxEventEntity entity = OutboxEventEntity.builder()
                .aggregateType("invoice")
                .aggregateId("inv-1")
                .eventType("invoice.created")
                .payload("{}")
                .build();

        assertEquals("invoice", entity.getAggregateType());
        assertEquals("inv-1", entity.getAggregateId());
        assertEquals("invoice.created", entity.getEventType());
        assertEquals("{}", entity.getPayload());
        assertEquals(OutboxPublishStatus.PENDING, entity.getPublishStatus());
        assertEquals(0, entity.getPublishAttempts());
    }

    @Test
    void claim_setsLease() {
        OutboxEventEntity entity = new OutboxEventEntity();
        Instant until = Instant.ofEpochSecond(1000);
        entity.claim("worker-1", until);

        assertEquals("worker-1", entity.getLockedBy());
        assertEquals(until, entity.getLockedUntil());
    }

    @Test
    void markPublished_clearsLeaseAndError() {
        OutboxEventEntity entity = new OutboxEventEntity();
        Instant publishedAt = Instant.ofEpochSecond(2000);
        entity.claim("worker-1", Instant.ofEpochSecond(9999));
        entity.markPublished(publishedAt);

        assertEquals(OutboxPublishStatus.PUBLISHED, entity.getPublishStatus());
        assertEquals(publishedAt, entity.getProcessedAt());
        assertNull(entity.getNextAttemptAt());
        assertNull(entity.getLastError());
        assertNull(entity.getLockedBy());
        assertNull(entity.getLockedUntil());
    }

    @Test
    void markPublishFailed_incrementsAttemptsAndSchedulesRetry() {
        OutboxEventEntity entity = new OutboxEventEntity();
        entity.claim("worker-1", Instant.ofEpochSecond(9999));
        Instant failedAt = Instant.ofEpochSecond(3000);
        entity.markPublishFailed("connect timeout", failedAt);

        assertEquals(OutboxPublishStatus.FAILED, entity.getPublishStatus());
        assertEquals(1, entity.getPublishAttempts());
        assertEquals("connect timeout", entity.getLastError());
        assertEquals(failedAt.plusSeconds(1), entity.getNextAttemptAt());
        assertNull(entity.getLockedBy());
        assertNull(entity.getLockedUntil());
    }

    @Test
    void markInvalid_marksInvalidAndClearsLease() {
        OutboxEventEntity entity = new OutboxEventEntity();
        entity.claim("worker-1", Instant.ofEpochSecond(9999));
        entity.markInvalid("schema mismatch");

        assertEquals(OutboxPublishStatus.INVALID, entity.getPublishStatus());
        assertEquals("schema mismatch", entity.getLastError());
        assertNull(entity.getNextAttemptAt());
        assertNull(entity.getLockedBy());
        assertNull(entity.getLockedUntil());
    }
}