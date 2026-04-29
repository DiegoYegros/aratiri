package com.aratiri.infrastructure.messaging.outbox;

import com.aratiri.infrastructure.messaging.KafkaTopics;
import com.aratiri.infrastructure.persistence.jpa.entity.OutboxEventEntity;
import com.aratiri.infrastructure.persistence.jpa.repository.OutboxEventRepository;
import com.aratiri.payments.application.dto.PayInvoiceRequestDTO;
import com.aratiri.payments.application.event.PaymentInitiatedEvent;
import com.aratiri.payments.application.event.PaymentSentEvent;
import com.aratiri.transactions.application.event.InternalInvoiceCancelEvent;
import com.aratiri.transactions.application.event.InternalTransferCompletedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxWriterServiceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private JsonMapper jsonMapper;

    private OutboxWriterService outboxWriterService;

    @BeforeEach
    void setUp() {
        outboxWriterService = new OutboxWriterService(outboxEventRepository, jsonMapper);
    }

    @Test
    void publishPaymentInitiated_ownsAggregateMetadataTopicAndSerialization() throws Exception {
        PayInvoiceRequestDTO request = new PayInvoiceRequestDTO();
        request.setInvoice("lnbc1test");
        request.setExternalReference("external-ref");
        request.setMetadata("{\"orderId\":\"order-123\"}");
        PaymentInitiatedEvent eventPayload = new PaymentInitiatedEvent("user-123", "tx-123", request);
        when(jsonMapper.writeValueAsString(eventPayload)).thenReturn("{\"payment\":\"initiated\"}");

        outboxWriterService.publishPaymentInitiated("tx-123", eventPayload);

        OutboxEventEntity event = savedEvent();
        assertEquals("LIGHTNING_INVOICE_PAYMENT", event.getAggregateType());
        assertEquals("tx-123", event.getAggregateId());
        assertEquals(KafkaTopics.PAYMENT_INITIATED.getCode(), event.getEventType());
        assertEquals("{\"payment\":\"initiated\"}", event.getPayload());
    }

    @Test
    void publishPaymentSent_ownsAggregateMetadataTopicAndSerialization() throws Exception {
        PaymentSentEvent eventPayload = new PaymentSentEvent(
                "user-123",
                "tx-123",
                1000L,
                "payhash",
                LocalDateTime.now(),
                "Lightning payment"
        );
        when(jsonMapper.writeValueAsString(eventPayload)).thenReturn("{\"payment\":\"sent\"}");

        outboxWriterService.publishPaymentSent("tx-123", eventPayload);

        OutboxEventEntity event = savedEvent();
        assertEquals("PAYMENT_SENT", event.getAggregateType());
        assertEquals("tx-123", event.getAggregateId());
        assertEquals(KafkaTopics.PAYMENT_SENT.getCode(), event.getEventType());
        assertEquals("{\"payment\":\"sent\"}", event.getPayload());
    }

    @Test
    void publishInternalTransferCompleted_ownsAggregateMetadataTopicAndSerialization() throws Exception {
        InternalTransferCompletedEvent eventPayload = new InternalTransferCompletedEvent(
                "sender-123",
                "receiver-123",
                2500L,
                "payment-hash",
                LocalDateTime.now(),
                "memo"
        );
        when(jsonMapper.writeValueAsString(eventPayload)).thenReturn("{\"transfer\":\"completed\"}");

        outboxWriterService.publishInternalTransferCompleted("tx-123", eventPayload);

        OutboxEventEntity event = savedEvent();
        assertEquals("INTERNAL_TRANSFER", event.getAggregateType());
        assertEquals("tx-123", event.getAggregateId());
        assertEquals(KafkaTopics.INTERNAL_TRANSFER_COMPLETED.getCode(), event.getEventType());
        assertEquals("{\"transfer\":\"completed\"}", event.getPayload());
    }

    @Test
    void publishInternalInvoiceCancel_ownsAggregateMetadataTopicAndSerialization() throws Exception {
        InternalInvoiceCancelEvent eventPayload = new InternalInvoiceCancelEvent("payment-hash");
        when(jsonMapper.writeValueAsString(eventPayload)).thenReturn("{\"paymentHash\":\"payment-hash\"}");

        outboxWriterService.publishInternalInvoiceCancel("payment-hash", eventPayload);

        OutboxEventEntity event = savedEvent();
        assertEquals("INTERNAL_INVOICE_CANCEL", event.getAggregateType());
        assertEquals("payment-hash", event.getAggregateId());
        assertEquals(KafkaTopics.INTERNAL_INVOICE_CANCEL.getCode(), event.getEventType());
        assertEquals("{\"paymentHash\":\"payment-hash\"}", event.getPayload());
    }

    private OutboxEventEntity savedEvent() {
        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventRepository).save(captor.capture());
        return captor.getValue();
    }
}
