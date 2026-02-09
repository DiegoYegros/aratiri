package com.aratiri.infrastructure.scheduling.job;

import com.aratiri.infrastructure.messaging.KafkaTopics;
import com.aratiri.infrastructure.messaging.producer.OutboxEventProducer;
import com.aratiri.infrastructure.persistence.jpa.entity.OutboxEventEntity;
import com.aratiri.infrastructure.persistence.jpa.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxEventJobTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private OutboxEventProducer outboxEventProducer;

    private OutboxEventJob outboxEventJob;

    @BeforeEach
    void setUp() {
        outboxEventJob = new OutboxEventJob(outboxEventRepository, outboxEventProducer);
    }

    @Test
    void processOutboxEvents_shouldDoNothingWhenNoEvents() {
        when(outboxEventRepository.findAndLockByProcessedAtIsNullOrderByCreatedAtAsc())
                .thenReturn(Collections.emptyList());

        outboxEventJob.processOutboxEvents();

        verify(outboxEventProducer, never()).sendEvent(any(), any());
    }

    @Test
    void processOutboxEvents_shouldProcessInvoiceSettledEvent() {
        OutboxEventEntity event = OutboxEventEntity.builder()
                .id(UUID.randomUUID())
                .eventType(KafkaTopics.INVOICE_SETTLED.getCode())
                .payload("{\"test\": \"payload\"}")
                .build();

        when(outboxEventRepository.findAndLockByProcessedAtIsNullOrderByCreatedAtAsc())
                .thenReturn(List.of(event));

        outboxEventJob.processOutboxEvents();

        verify(outboxEventProducer).sendEvent(KafkaTopics.INVOICE_SETTLED, "{\"test\": \"payload\"}");

        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventRepository).save(captor.capture());
        assertNotNull(captor.getValue().getProcessedAt());
    }

    @Test
    void processOutboxEvents_shouldProcessPaymentInitiatedEvent() {
        OutboxEventEntity event = OutboxEventEntity.builder()
                .id(UUID.randomUUID())
                .eventType(KafkaTopics.PAYMENT_INITIATED.getCode())
                .payload("{\"payment\": \"data\"}")
                .build();

        when(outboxEventRepository.findAndLockByProcessedAtIsNullOrderByCreatedAtAsc())
                .thenReturn(List.of(event));

        outboxEventJob.processOutboxEvents();

        verify(outboxEventProducer).sendEvent(KafkaTopics.PAYMENT_INITIATED, "{\"payment\": \"data\"}");
    }

    @Test
    void processOutboxEvents_shouldProcessOnChainPaymentEvent() {
        OutboxEventEntity event = OutboxEventEntity.builder()
                .id(UUID.randomUUID())
                .eventType(KafkaTopics.ONCHAIN_PAYMENT_INITIATED.getCode())
                .payload("{\"onchain\": \"data\"}")
                .build();

        when(outboxEventRepository.findAndLockByProcessedAtIsNullOrderByCreatedAtAsc())
                .thenReturn(List.of(event));

        outboxEventJob.processOutboxEvents();

        verify(outboxEventProducer).sendEvent(KafkaTopics.ONCHAIN_PAYMENT_INITIATED, "{\"onchain\": \"data\"}");
    }

    @Test
    void processOutboxEvents_shouldProcessInternalTransferInitiatedEvent() {
        OutboxEventEntity event = OutboxEventEntity.builder()
                .id(UUID.randomUUID())
                .eventType(KafkaTopics.INTERNAL_TRANSFER_INITIATED.getCode())
                .payload("{\"transfer\": \"data\"}")
                .build();

        when(outboxEventRepository.findAndLockByProcessedAtIsNullOrderByCreatedAtAsc())
                .thenReturn(List.of(event));

        outboxEventJob.processOutboxEvents();

        verify(outboxEventProducer).sendEvent(KafkaTopics.INTERNAL_TRANSFER_INITIATED, "{\"transfer\": \"data\"}");
    }

    @Test
    void processOutboxEvents_shouldProcessInternalTransferCompletedEvent() {
        OutboxEventEntity event = OutboxEventEntity.builder()
                .id(UUID.randomUUID())
                .eventType(KafkaTopics.INTERNAL_TRANSFER_COMPLETED.getCode())
                .payload("{\"completed\": \"data\"}")
                .build();

        when(outboxEventRepository.findAndLockByProcessedAtIsNullOrderByCreatedAtAsc())
                .thenReturn(List.of(event));

        outboxEventJob.processOutboxEvents();

        verify(outboxEventProducer).sendEvent(KafkaTopics.INTERNAL_TRANSFER_COMPLETED, "{\"completed\": \"data\"}");
    }

    @Test
    void processOutboxEvents_shouldProcessPaymentSentEvent() {
        OutboxEventEntity event = OutboxEventEntity.builder()
                .id(UUID.randomUUID())
                .eventType(KafkaTopics.PAYMENT_SENT.getCode())
                .payload("{\"sent\": \"data\"}")
                .build();

        when(outboxEventRepository.findAndLockByProcessedAtIsNullOrderByCreatedAtAsc())
                .thenReturn(List.of(event));

        outboxEventJob.processOutboxEvents();

        verify(outboxEventProducer).sendEvent(KafkaTopics.PAYMENT_SENT, "{\"sent\": \"data\"}");
    }

    @Test
    void processOutboxEvents_shouldProcessOnChainTransactionReceivedEvent() {
        OutboxEventEntity event = OutboxEventEntity.builder()
                .id(UUID.randomUUID())
                .eventType(KafkaTopics.ONCHAIN_TRANSACTION_RECEIVED.getCode())
                .payload("{\"received\": \"data\"}")
                .build();

        when(outboxEventRepository.findAndLockByProcessedAtIsNullOrderByCreatedAtAsc())
                .thenReturn(List.of(event));

        outboxEventJob.processOutboxEvents();

        verify(outboxEventProducer).sendEvent(KafkaTopics.ONCHAIN_TRANSACTION_RECEIVED, "{\"received\": \"data\"}");
    }

    @Test
    void processOutboxEvents_shouldProcessInternalInvoiceCancelEvent() {
        OutboxEventEntity event = OutboxEventEntity.builder()
                .id(UUID.randomUUID())
                .eventType(KafkaTopics.INTERNAL_INVOICE_CANCEL.getCode())
                .payload("{\"paymentHash\": \"abc\"}")
                .build();

        when(outboxEventRepository.findAndLockByProcessedAtIsNullOrderByCreatedAtAsc())
                .thenReturn(List.of(event));

        outboxEventJob.processOutboxEvents();

        verify(outboxEventProducer).sendEvent(KafkaTopics.INTERNAL_INVOICE_CANCEL, "{\"paymentHash\": \"abc\"}");
    }

    @Test
    void processOutboxEvents_shouldIgnoreUnknownEventType() {
        OutboxEventEntity event = OutboxEventEntity.builder()
                .id(UUID.randomUUID())
                .eventType("unknown.event.type")
                .payload("{\"unknown\": \"data\"}")
                .build();

        when(outboxEventRepository.findAndLockByProcessedAtIsNullOrderByCreatedAtAsc())
                .thenReturn(List.of(event));

        outboxEventJob.processOutboxEvents();

        verify(outboxEventProducer, never()).sendEvent(any(), any());
        verify(outboxEventRepository, never()).save(any());
    }
}
