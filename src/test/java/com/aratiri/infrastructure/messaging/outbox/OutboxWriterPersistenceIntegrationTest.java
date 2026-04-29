package com.aratiri.infrastructure.messaging.outbox;

import com.aratiri.AbstractIntegrationTest;
import com.aratiri.accounts.application.port.out.CurrencyConversionPort;
import com.aratiri.accounts.application.port.out.LightningAddressPort;
import com.aratiri.auth.application.port.out.EmailNotificationPort;
import com.aratiri.infrastructure.messaging.KafkaTopics;
import com.aratiri.infrastructure.messaging.consumer.NotificationConsumer;
import com.aratiri.infrastructure.messaging.listener.LightningListener;
import com.aratiri.infrastructure.messaging.listener.OnChainTransactionListener;
import com.aratiri.infrastructure.persistence.jpa.entity.OutboxEventEntity;
import com.aratiri.infrastructure.persistence.jpa.repository.OutboxEventRepository;
import com.aratiri.payments.application.dto.PayInvoiceRequestDTO;
import com.aratiri.payments.application.event.PaymentInitiatedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OutboxWriterPersistenceIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean
    private EmailNotificationPort emailNotificationPort;

    @MockitoBean
    private CurrencyConversionPort currencyConversionPort;

    @MockitoBean
    private LightningAddressPort lightningAddressPort;

    @MockitoBean
    private LightningListener lightningListener;

    @MockitoBean
    private OnChainTransactionListener onChainTransactionListener;

    @MockitoBean
    private NotificationConsumer notificationConsumer;

    @Autowired
    private OutboxWriter outboxWriter;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Test
    @DisplayName("Payment initiated writer commits an unprocessed outbox row with metadata, topic, and payload")
    void paymentInitiatedWriter_commitsDurableOutboxEvent() {
        PayInvoiceRequestDTO request = new PayInvoiceRequestDTO();
        request.setInvoice("lnbc1paymentrequest");
        request.setExternalReference("external-ref-123");
        request.setMetadata("{\"orderId\":\"order-123\"}");

        outboxWriter.publishPaymentInitiated(
                "tx-123",
                new PaymentInitiatedEvent("user-123", "tx-123", request)
        );
        outboxEventRepository.flush();

        List<OutboxEventEntity> events = outboxEventRepository.findAll();
        assertEquals(1, events.size());

        OutboxEventEntity event = events.getFirst();
        assertNotNull(event.getId());
        assertEquals("LIGHTNING_INVOICE_PAYMENT", event.getAggregateType());
        assertEquals("tx-123", event.getAggregateId());
        assertEquals(KafkaTopics.PAYMENT_INITIATED.getCode(), event.getEventType());
        assertTrue(event.getPayload().contains("\"userId\":\"user-123\""));
        assertTrue(event.getPayload().contains("\"transactionId\":\"tx-123\""));
        assertTrue(event.getPayload().contains("\"invoice\":\"lnbc1paymentrequest\""));
        assertNull(event.getProcessedAt());
        assertNotNull(event.getCreatedAt());
    }
}
