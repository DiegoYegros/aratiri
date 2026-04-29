package com.aratiri.infrastructure.messaging.outbox;

import com.aratiri.infrastructure.messaging.KafkaTopics;
import com.aratiri.payments.application.event.PaymentInitiatedEvent;

public record PaymentInitiatedCommittedEvent(
        String transactionId,
        PaymentInitiatedEvent payload
) implements CommittedEvent {

    private static final String AGGREGATE_TYPE = "LIGHTNING_INVOICE_PAYMENT";

    @Override
    public String aggregateType() {
        return AGGREGATE_TYPE;
    }

    @Override
    public String aggregateId() {
        return transactionId;
    }

    @Override
    public KafkaTopics topic() {
        return KafkaTopics.PAYMENT_INITIATED;
    }
}
