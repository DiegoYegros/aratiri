package com.aratiri.infrastructure.scheduling.job;

import com.aratiri.infrastructure.messaging.KafkaTopics;
import com.aratiri.infrastructure.messaging.producer.OutboxEventProducer;
import com.aratiri.infrastructure.persistence.jpa.entity.OutboxEventEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.OutboxPublishStatus;
import com.aratiri.infrastructure.persistence.jpa.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
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
        ReflectionTestUtils.setField(outboxEventJob, "batchSize", 200);
    }

    @Test
    void processOutboxEvents_shouldDoNothingWhenNoEvents() {
        when(outboxEventRepository.findPublishableEvents(any(Instant.class), anyCollection(), any(Pageable.class)))
                .thenReturn(Collections.emptyList());

        outboxEventJob.processOutboxEvents();

        verify(outboxEventProducer, never()).sendEvent(any(), any(), any());
    }

    @Test
    void processOutboxEvents_queriesWithConfiguredBatchSize() {
        ReflectionTestUtils.setField(outboxEventJob, "batchSize", 2);
        when(outboxEventRepository.findPublishableEvents(any(Instant.class), anyCollection(), any(Pageable.class)))
                .thenReturn(Collections.emptyList());

        outboxEventJob.processOutboxEvents();

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(outboxEventRepository).findPublishableEvents(any(Instant.class), anyCollection(), pageableCaptor.capture());
        assertEquals(0, pageableCaptor.getValue().getPageNumber());
        assertEquals(2, pageableCaptor.getValue().getPageSize());
    }

    @Test
    void processOutboxEvents_shouldProcessInvoiceSettledEvent() {
        OutboxEventEntity event = OutboxEventEntity.builder()
                .id(UUID.randomUUID()).aggregateId("agg-1")
                .eventType(KafkaTopics.INVOICE_SETTLED.getCode())
                .payload("{\"test\": \"payload\"}")
                .build();

        when(outboxEventRepository.findPublishableEvents(any(Instant.class), anyCollection(), any(Pageable.class)))
                .thenReturn(List.of(event));

        outboxEventJob.processOutboxEvents();

        verify(outboxEventProducer).sendEvent(KafkaTopics.INVOICE_SETTLED, "agg-1", "{\"test\": \"payload\"}");

        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventRepository).save(captor.capture());
        assertNotNull(captor.getValue().getProcessedAt());
        assertEquals(OutboxPublishStatus.PUBLISHED, captor.getValue().getPublishStatus());
        assertNull(captor.getValue().getLastError());
    }

    @Test
    void processOutboxEvents_shouldProcessPaymentInitiatedEvent() {
        OutboxEventEntity event = OutboxEventEntity.builder()
                .id(UUID.randomUUID()).aggregateId("agg-1")
                .eventType(KafkaTopics.PAYMENT_INITIATED.getCode())
                .payload("{\"payment\": \"data\"}")
                .build();

        when(outboxEventRepository.findPublishableEvents(any(Instant.class), anyCollection(), any(Pageable.class)))
                .thenReturn(List.of(event));

        outboxEventJob.processOutboxEvents();

        verify(outboxEventProducer).sendEvent(KafkaTopics.PAYMENT_INITIATED, "agg-1", "{\"payment\": \"data\"}");
    }

    @Test
    void processOutboxEvents_shouldProcessOnChainPaymentEvent() {
        OutboxEventEntity event = OutboxEventEntity.builder()
                .id(UUID.randomUUID()).aggregateId("agg-1")
                .eventType(KafkaTopics.ONCHAIN_PAYMENT_INITIATED.getCode())
                .payload("{\"onchain\": \"data\"}")
                .build();

        when(outboxEventRepository.findPublishableEvents(any(Instant.class), anyCollection(), any(Pageable.class)))
                .thenReturn(List.of(event));

        outboxEventJob.processOutboxEvents();

        verify(outboxEventProducer).sendEvent(KafkaTopics.ONCHAIN_PAYMENT_INITIATED, "agg-1", "{\"onchain\": \"data\"}");
    }

    @Test
    void processOutboxEvents_shouldProcessInternalTransferInitiatedEvent() {
        OutboxEventEntity event = OutboxEventEntity.builder()
                .id(UUID.randomUUID()).aggregateId("agg-1")
                .eventType(KafkaTopics.INTERNAL_TRANSFER_INITIATED.getCode())
                .payload("{\"transfer\": \"data\"}")
                .build();

        when(outboxEventRepository.findPublishableEvents(any(Instant.class), anyCollection(), any(Pageable.class)))
                .thenReturn(List.of(event));

        outboxEventJob.processOutboxEvents();

        verify(outboxEventProducer).sendEvent(KafkaTopics.INTERNAL_TRANSFER_INITIATED, "agg-1", "{\"transfer\": \"data\"}");
    }

    @Test
    void processOutboxEvents_shouldProcessInternalTransferCompletedEvent() {
        OutboxEventEntity event = OutboxEventEntity.builder()
                .id(UUID.randomUUID()).aggregateId("agg-1")
                .eventType(KafkaTopics.INTERNAL_TRANSFER_COMPLETED.getCode())
                .payload("{\"completed\": \"data\"}")
                .build();

        when(outboxEventRepository.findPublishableEvents(any(Instant.class), anyCollection(), any(Pageable.class)))
                .thenReturn(List.of(event));

        outboxEventJob.processOutboxEvents();

        verify(outboxEventProducer).sendEvent(KafkaTopics.INTERNAL_TRANSFER_COMPLETED, "agg-1", "{\"completed\": \"data\"}");
    }

    @Test
    void processOutboxEvents_shouldProcessPaymentSentEvent() {
        OutboxEventEntity event = OutboxEventEntity.builder()
                .id(UUID.randomUUID()).aggregateId("agg-1")
                .eventType(KafkaTopics.PAYMENT_SENT.getCode())
                .payload("{\"sent\": \"data\"}")
                .build();

        when(outboxEventRepository.findPublishableEvents(any(Instant.class), anyCollection(), any(Pageable.class)))
                .thenReturn(List.of(event));

        outboxEventJob.processOutboxEvents();

        verify(outboxEventProducer).sendEvent(KafkaTopics.PAYMENT_SENT, "agg-1", "{\"sent\": \"data\"}");
    }

    @Test
    void processOutboxEvents_shouldProcessOnChainTransactionReceivedEvent() {
        OutboxEventEntity event = OutboxEventEntity.builder()
                .id(UUID.randomUUID()).aggregateId("agg-1")
                .eventType(KafkaTopics.ONCHAIN_TRANSACTION_RECEIVED.getCode())
                .payload("{\"received\": \"data\"}")
                .build();

        when(outboxEventRepository.findPublishableEvents(any(Instant.class), anyCollection(), any(Pageable.class)))
                .thenReturn(List.of(event));

        outboxEventJob.processOutboxEvents();

        verify(outboxEventProducer).sendEvent(KafkaTopics.ONCHAIN_TRANSACTION_RECEIVED, "agg-1", "{\"received\": \"data\"}");
    }

    @Test
    void processOutboxEvents_shouldProcessInternalInvoiceCancelEvent() {
        OutboxEventEntity event = OutboxEventEntity.builder()
                .id(UUID.randomUUID()).aggregateId("agg-1")
                .eventType(KafkaTopics.INTERNAL_INVOICE_CANCEL.getCode())
                .payload("{\"paymentHash\": \"abc\"}")
                .build();

        when(outboxEventRepository.findPublishableEvents(any(Instant.class), anyCollection(), any(Pageable.class)))
                .thenReturn(List.of(event));

        outboxEventJob.processOutboxEvents();

        verify(outboxEventProducer).sendEvent(KafkaTopics.INTERNAL_INVOICE_CANCEL, "agg-1", "{\"paymentHash\": \"abc\"}");
    }

    @Test
    void processOutboxEvents_shouldMarkUnknownEventTypeInvalid() {
        OutboxEventEntity event = OutboxEventEntity.builder()
                .id(UUID.randomUUID()).aggregateId("agg-1")
                .eventType("unknown.event.type")
                .payload("{\"unknown\": \"data\"}")
                .build();

        when(outboxEventRepository.findPublishableEvents(any(Instant.class), anyCollection(), any(Pageable.class)))
                .thenReturn(List.of(event));

        outboxEventJob.processOutboxEvents();

        verify(outboxEventProducer, never()).sendEvent(any(), any(), any());
        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventRepository).save(captor.capture());
        assertEquals(OutboxPublishStatus.INVALID, captor.getValue().getPublishStatus());
        assertNull(captor.getValue().getProcessedAt());
        assertTrue(captor.getValue().getLastError().contains("unknown.event.type"));
    }

    @Test
    void processOutboxEvents_shouldRecordFailureWithoutMarkingProcessedSoItCanRetry() {
        OutboxEventEntity event = OutboxEventEntity.builder()
                .id(UUID.randomUUID()).aggregateId("agg-1")
                .eventType(KafkaTopics.PAYMENT_INITIATED.getCode())
                .payload("{\"payment\": \"data\"}")
                .build();

        when(outboxEventRepository.findPublishableEvents(any(Instant.class), anyCollection(), any(Pageable.class)))
                .thenReturn(List.of(event));
        doThrow(new IllegalStateException("kafka unavailable"))
                .when(outboxEventProducer).sendEvent(KafkaTopics.PAYMENT_INITIATED, "agg-1", "{\"payment\": \"data\"}");

        outboxEventJob.processOutboxEvents();

        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventRepository).save(captor.capture());
        OutboxEventEntity failed = captor.getValue();
        assertEquals(OutboxPublishStatus.FAILED, failed.getPublishStatus());
        assertEquals(1, failed.getPublishAttempts());
        assertNull(failed.getProcessedAt());
        assertEquals("kafka unavailable", failed.getLastError());
        assertNotNull(failed.getNextAttemptAt());
    }
}
