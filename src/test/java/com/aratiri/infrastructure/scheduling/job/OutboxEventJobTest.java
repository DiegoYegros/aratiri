package com.aratiri.infrastructure.scheduling.job;

import com.aratiri.infrastructure.messaging.KafkaTopics;
import com.aratiri.infrastructure.messaging.producer.OutboxEventProducer;
import com.aratiri.infrastructure.persistence.jpa.entity.OutboxEventEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxEventJobTest {

    @Mock
    private OutboxEventClaimer outboxEventClaimer;

    @Mock
    private OutboxEventProducer outboxEventProducer;

    private OutboxEventJob outboxEventJob;

    @BeforeEach
    void setUp() {
        outboxEventJob = new OutboxEventJob(outboxEventClaimer, outboxEventProducer);
    }

    @Test
    void processOutboxEvents_shouldDoNothingWhenNoEvents() {
        when(outboxEventClaimer.claimBatch()).thenReturn(Collections.emptyList());

        outboxEventJob.processOutboxEvents();

        verify(outboxEventProducer, never()).sendEvent(any(), any(), any());
        verify(outboxEventClaimer, never()).markPublished(any());
    }

    @Test
    void processOutboxEvents_shouldProcessInvoiceSettledEvent() {
        OutboxEventEntity event = claimedEvent(KafkaTopics.INVOICE_SETTLED.getCode(), "agg-1", "{\"test\": \"payload\"}");
        when(outboxEventClaimer.claimBatch()).thenReturn(List.of(event));
        when(outboxEventClaimer.markPublished(event)).thenReturn(1);

        outboxEventJob.processOutboxEvents();

        verify(outboxEventProducer).sendEvent(KafkaTopics.INVOICE_SETTLED, "agg-1", "{\"test\": \"payload\"}");
        verify(outboxEventClaimer).markPublished(event);
    }

    @Test
    void processOutboxEvents_shouldProcessPaymentInitiatedEvent() {
        OutboxEventEntity event = claimedEvent(KafkaTopics.PAYMENT_INITIATED.getCode(), "agg-1", "{\"payment\": \"data\"}");
        when(outboxEventClaimer.claimBatch()).thenReturn(List.of(event));
        when(outboxEventClaimer.markPublished(event)).thenReturn(1);

        outboxEventJob.processOutboxEvents();

        verify(outboxEventProducer).sendEvent(KafkaTopics.PAYMENT_INITIATED, "agg-1", "{\"payment\": \"data\"}");
    }

    @Test
    void processOutboxEvents_shouldProcessOnChainPaymentEvent() {
        OutboxEventEntity event = claimedEvent(KafkaTopics.ONCHAIN_PAYMENT_INITIATED.getCode(), "agg-1", "{\"onchain\": \"data\"}");
        when(outboxEventClaimer.claimBatch()).thenReturn(List.of(event));
        when(outboxEventClaimer.markPublished(event)).thenReturn(1);

        outboxEventJob.processOutboxEvents();

        verify(outboxEventProducer).sendEvent(KafkaTopics.ONCHAIN_PAYMENT_INITIATED, "agg-1", "{\"onchain\": \"data\"}");
    }

    @Test
    void processOutboxEvents_shouldProcessInternalTransferInitiatedEvent() {
        OutboxEventEntity event = claimedEvent(KafkaTopics.INTERNAL_TRANSFER_INITIATED.getCode(), "agg-1", "{\"transfer\": \"data\"}");
        when(outboxEventClaimer.claimBatch()).thenReturn(List.of(event));
        when(outboxEventClaimer.markPublished(event)).thenReturn(1);

        outboxEventJob.processOutboxEvents();

        verify(outboxEventProducer).sendEvent(KafkaTopics.INTERNAL_TRANSFER_INITIATED, "agg-1", "{\"transfer\": \"data\"}");
    }

    @Test
    void processOutboxEvents_shouldProcessInternalTransferCompletedEvent() {
        OutboxEventEntity event = claimedEvent(KafkaTopics.INTERNAL_TRANSFER_COMPLETED.getCode(), "agg-1", "{\"completed\": \"data\"}");
        when(outboxEventClaimer.claimBatch()).thenReturn(List.of(event));
        when(outboxEventClaimer.markPublished(event)).thenReturn(1);

        outboxEventJob.processOutboxEvents();

        verify(outboxEventProducer).sendEvent(KafkaTopics.INTERNAL_TRANSFER_COMPLETED, "agg-1", "{\"completed\": \"data\"}");
    }

    @Test
    void processOutboxEvents_shouldProcessPaymentSentEvent() {
        OutboxEventEntity event = claimedEvent(KafkaTopics.PAYMENT_SENT.getCode(), "agg-1", "{\"sent\": \"data\"}");
        when(outboxEventClaimer.claimBatch()).thenReturn(List.of(event));
        when(outboxEventClaimer.markPublished(event)).thenReturn(1);

        outboxEventJob.processOutboxEvents();

        verify(outboxEventProducer).sendEvent(KafkaTopics.PAYMENT_SENT, "agg-1", "{\"sent\": \"data\"}");
    }

    @Test
    void processOutboxEvents_shouldProcessOnChainTransactionReceivedEvent() {
        OutboxEventEntity event = claimedEvent(KafkaTopics.ONCHAIN_TRANSACTION_RECEIVED.getCode(), "agg-1", "{\"received\": \"data\"}");
        when(outboxEventClaimer.claimBatch()).thenReturn(List.of(event));
        when(outboxEventClaimer.markPublished(event)).thenReturn(1);

        outboxEventJob.processOutboxEvents();

        verify(outboxEventProducer).sendEvent(KafkaTopics.ONCHAIN_TRANSACTION_RECEIVED, "agg-1", "{\"received\": \"data\"}");
    }

    @Test
    void processOutboxEvents_shouldProcessInternalInvoiceCancelEvent() {
        OutboxEventEntity event = claimedEvent(KafkaTopics.INTERNAL_INVOICE_CANCEL.getCode(), "agg-1", "{\"paymentHash\": \"abc\"}");
        when(outboxEventClaimer.claimBatch()).thenReturn(List.of(event));
        when(outboxEventClaimer.markPublished(event)).thenReturn(1);

        outboxEventJob.processOutboxEvents();

        verify(outboxEventProducer).sendEvent(KafkaTopics.INTERNAL_INVOICE_CANCEL, "agg-1", "{\"paymentHash\": \"abc\"}");
    }

    @Test
    void processOutboxEvents_shouldMarkUnknownEventTypeInvalid() {
        OutboxEventEntity event = claimedEvent("unknown.event.type", "agg-1", "{\"unknown\": \"data\"}");
        when(outboxEventClaimer.claimBatch()).thenReturn(List.of(event));
        when(outboxEventClaimer.markInvalid(event, "Unknown outbox event type: unknown.event.type")).thenReturn(1);

        outboxEventJob.processOutboxEvents();

        verify(outboxEventProducer, never()).sendEvent(any(), any(), any());
        verify(outboxEventClaimer).markInvalid(event, "Unknown outbox event type: unknown.event.type");
        verify(outboxEventClaimer, never()).markPublished(any());
    }

    @Test
    void processOutboxEvents_shouldRecordFailureWithoutMarkingProcessedSoItCanRetry() {
        OutboxEventEntity event = claimedEvent(KafkaTopics.PAYMENT_INITIATED.getCode(), "agg-1", "{\"payment\": \"data\"}");
        when(outboxEventClaimer.claimBatch()).thenReturn(List.of(event));
        doThrow(new IllegalStateException("kafka unavailable"))
                .when(outboxEventProducer).sendEvent(KafkaTopics.PAYMENT_INITIATED, "agg-1", "{\"payment\": \"data\"}");
        when(outboxEventClaimer.markPublishFailed(event, "kafka unavailable")).thenReturn(1);

        outboxEventJob.processOutboxEvents();

        verify(outboxEventClaimer).markPublishFailed(event, "kafka unavailable");
        verify(outboxEventClaimer, never()).markPublished(any());
    }

    @Test
    void processOutboxEvents_sendsKafkaOutsideClaimTransaction() {
        OutboxEventEntity event = claimedEvent(KafkaTopics.PAYMENT_SENT.getCode(), "agg-1", "{\"sent\": \"data\"}");
        when(outboxEventClaimer.claimBatch()).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return List.of(event);
        });
        doAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return null;
        }).when(outboxEventProducer).sendEvent(any(), any(), any());
        when(outboxEventClaimer.markPublished(event)).thenReturn(1);

        outboxEventJob.processOutboxEvents();

        InOrder inOrder = inOrder(outboxEventClaimer, outboxEventProducer);
        inOrder.verify(outboxEventClaimer).claimBatch();
        inOrder.verify(outboxEventProducer).sendEvent(KafkaTopics.PAYMENT_SENT, "agg-1", "{\"sent\": \"data\"}");
        inOrder.verify(outboxEventClaimer).markPublished(event);
    }

    private static OutboxEventEntity claimedEvent(String eventType, String aggregateId, String payload) {
        OutboxEventEntity event = OutboxEventEntity.builder()
                .id(UUID.randomUUID())
                .aggregateId(aggregateId)
                .eventType(eventType)
                .payload(payload)
                .build();
        event.claim("outbox-test-token", java.time.Instant.now().plusSeconds(30));
        return event;
    }
}
