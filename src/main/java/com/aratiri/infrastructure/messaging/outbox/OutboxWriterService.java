package com.aratiri.infrastructure.messaging.outbox;

import com.aratiri.infrastructure.messaging.KafkaTopics;
import com.aratiri.infrastructure.persistence.jpa.entity.OutboxEventEntity;
import com.aratiri.infrastructure.persistence.jpa.repository.OutboxEventRepository;
import com.aratiri.payments.application.event.PaymentInitiatedEvent;
import com.aratiri.payments.application.event.PaymentSentEvent;
import com.aratiri.shared.exception.AratiriException;
import com.aratiri.transactions.application.event.InternalInvoiceCancelEvent;
import com.aratiri.transactions.application.event.InternalTransferCompletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class OutboxWriterService implements OutboxWriter {

    private static final String PAYMENT_INITIATED_AGGREGATE_TYPE = "LIGHTNING_INVOICE_PAYMENT";
    private static final String PAYMENT_SENT_AGGREGATE_TYPE = "PAYMENT_SENT";
    private static final String INTERNAL_TRANSFER_AGGREGATE_TYPE = "INTERNAL_TRANSFER";
    private static final String INTERNAL_INVOICE_CANCEL_AGGREGATE_TYPE = "INTERNAL_INVOICE_CANCEL";

    private final OutboxEventRepository outboxEventRepository;
    private final JsonMapper jsonMapper;

    @Override
    public void publishPaymentInitiated(String transactionId, PaymentInitiatedEvent eventPayload) {
        publish(PAYMENT_INITIATED_AGGREGATE_TYPE, transactionId, KafkaTopics.PAYMENT_INITIATED, eventPayload);
    }

    @Override
    public void publishPaymentSent(String transactionId, PaymentSentEvent eventPayload) {
        publish(PAYMENT_SENT_AGGREGATE_TYPE, transactionId, KafkaTopics.PAYMENT_SENT, eventPayload);
    }

    @Override
    public void publishInternalTransferCompleted(String transactionId, InternalTransferCompletedEvent eventPayload) {
        publish(INTERNAL_TRANSFER_AGGREGATE_TYPE, transactionId, KafkaTopics.INTERNAL_TRANSFER_COMPLETED, eventPayload);
    }

    @Override
    public void publishInternalInvoiceCancel(String paymentHash, InternalInvoiceCancelEvent eventPayload) {
        publish(INTERNAL_INVOICE_CANCEL_AGGREGATE_TYPE, paymentHash, KafkaTopics.INTERNAL_INVOICE_CANCEL, eventPayload);
    }

    private void publish(String aggregateType, String aggregateId, KafkaTopics topic, Object eventPayload) {
        try {
            OutboxEventEntity outboxEvent = OutboxEventEntity.builder()
                    .aggregateType(aggregateType)
                    .aggregateId(aggregateId)
                    .eventType(topic.getCode())
                    .payload(jsonMapper.writeValueAsString(eventPayload))
                    .build();
            outboxEventRepository.save(outboxEvent);
        } catch (Exception e) {
            throw new AratiriException("Failed to create outbox event.", HttpStatus.INTERNAL_SERVER_ERROR.value(), e);
        }
    }
}
