package com.aratiri.infrastructure.persistence.jpa.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class NodeOperationEntityTest {

    @Test
    void builder_createsEntity() {
        NodeOperationEntity entity = NodeOperationEntity.builder()
                .transactionId("tx-1")
                .userId("user-1")
                .operationType(NodeOperationType.LIGHTNING_PAYMENT)
                .status(NodeOperationStatus.IN_PROGRESS)
                .requestPayload("{}")
                .attemptCount(0)
                .nextAttemptAt(Instant.now())
                .build();

        assertEquals("tx-1", entity.getTransactionId());
        assertEquals("user-1", entity.getUserId());
        assertEquals(NodeOperationType.LIGHTNING_PAYMENT, entity.getOperationType());
        assertEquals(NodeOperationStatus.IN_PROGRESS, entity.getStatus());
        assertEquals(0, entity.getAttemptCount());
    }

    @Test
    void ensureDefaults_setsDefaultsWhenNull() {
        NodeOperationEntity entity = new NodeOperationEntity();
        Instant before = Instant.now();
        entity.ensureDefaults();
        Instant after = Instant.now();

        assertEquals(0, entity.getAttemptCount());
        assertEquals(NodeOperationStatus.PENDING, entity.getStatus());
        assertNotNull(entity.getNextAttemptAt());
        assertTrue(!entity.getNextAttemptAt().isBefore(before) && !entity.getNextAttemptAt().isAfter(after),
                "nextAttemptAt should be set near now");
    }

    @Test
    void ensureDefaults_doesNotOverrideExistingValues() {
        NodeOperationEntity entity = new NodeOperationEntity();
        entity.setAttemptCount(3);
        entity.setStatus(NodeOperationStatus.FAILED);
        entity.setNextAttemptAt(Instant.EPOCH);
        entity.ensureDefaults();

        assertEquals(3, entity.getAttemptCount());
        assertEquals(NodeOperationStatus.FAILED, entity.getStatus());
        assertEquals(Instant.EPOCH, entity.getNextAttemptAt());
    }

    @Test
    void setters_work() {
        NodeOperationEntity entity = new NodeOperationEntity();
        entity.setReferenceId("ref-1");
        entity.setExternalId("ext-1");
        entity.setLockedBy("worker-1");
        entity.setLockedUntil(Instant.ofEpochSecond(1000));
        entity.setLastError("boom");
        entity.setCompletedAt(Instant.ofEpochSecond(2000));

        assertEquals("ref-1", entity.getReferenceId());
        assertEquals("ext-1", entity.getExternalId());
        assertEquals("worker-1", entity.getLockedBy());
        assertEquals(Instant.ofEpochSecond(1000), entity.getLockedUntil());
        assertEquals("boom", entity.getLastError());
        assertEquals(Instant.ofEpochSecond(2000), entity.getCompletedAt());
    }
}