package com.aratiri.infrastructure.persistence.jpa.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PaymentCommandEntityTest {

    @Test
    void builder_createsEntity() {
        PaymentCommandEntity entity = PaymentCommandEntity.builder()
                .userId("user-1")
                .idempotencyKey("key-1")
                .commandType("PAY_INVOICE")
                .requestHash("hash-1")
                .build();

        assertEquals("user-1", entity.getUserId());
        assertEquals("key-1", entity.getIdempotencyKey());
        assertEquals("PAY_INVOICE", entity.getCommandType());
        assertEquals("hash-1", entity.getRequestHash());
    }

    @Test
    void prePersist_setsDefaultStatusWhenNull() {
        PaymentCommandEntity entity = new PaymentCommandEntity();
        entity.ensureDefaults();

        assertEquals("IN_PROGRESS", entity.getStatus());
    }

    @Test
    void prePersist_doesNotOverrideExistingStatus() {
        PaymentCommandEntity entity = new PaymentCommandEntity();
        entity.setStatus("FAILED");
        entity.ensureDefaults();

        assertEquals("FAILED", entity.getStatus());
    }

    @Test
    void noArgsConstructor_createsInstance() {
        PaymentCommandEntity entity = new PaymentCommandEntity();
        assertNotNull(entity);
    }

    @Test
    void allArgsConstructor_createsInstance() {
        PaymentCommandEntity entity = new PaymentCommandEntity(null, "user-1", "key-1", "PAY_INVOICE",
                "hash-1", null, null, "IN_PROGRESS", null, null);
        assertEquals("user-1", entity.getUserId());
    }

    @Test
    void setters_work() {
        PaymentCommandEntity entity = new PaymentCommandEntity();
        entity.setTransactionId("tx-1");
        entity.setResponsePayload("{}");

        assertEquals("tx-1", entity.getTransactionId());
        assertEquals("{}", entity.getResponsePayload());
    }
}
