package com.aratiri.payments.domain;

public record OutboxMessage(
        String aggregateType,
        String aggregateId,
        String eventType,
        String payload
) {
}
