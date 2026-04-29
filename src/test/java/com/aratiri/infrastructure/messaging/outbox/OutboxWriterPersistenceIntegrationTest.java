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
import com.aratiri.invoices.application.event.InvoiceSettledEvent;
import com.aratiri.payments.application.dto.OnChainPaymentDTOs;
import com.aratiri.payments.application.dto.PayInvoiceRequestDTO;
import com.aratiri.payments.application.event.OnChainPaymentInitiatedEvent;
import com.aratiri.payments.application.event.PaymentInitiatedEvent;
import com.aratiri.transactions.application.event.InternalTransferInitiatedEvent;
import com.aratiri.transactions.application.event.OnChainTransactionReceivedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
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

    @Test
    @DisplayName("Migrated invoice, on-chain, and internal transfer writers commit durable outbox rows")
    void migratedWriters_commitDurableOutboxEventsWithExpectedMetadata() {
        OnChainPaymentDTOs.SendOnChainRequestDTO onChainRequest = new OnChainPaymentDTOs.SendOnChainRequestDTO();
        onChainRequest.setAddress("bc1qrecipient");
        onChainRequest.setSatsAmount(2_000L);

        outboxWriter.publishInvoiceSettled(
                "invoice-123",
                new InvoiceSettledEvent("user-123", 1_000L, "payment-hash", LocalDateTime.now(), "memo")
        );
        outboxWriter.publishOnChainPaymentInitiated(
                "tx-onchain",
                new OnChainPaymentInitiatedEvent("user-123", "tx-onchain", onChainRequest)
        );
        outboxWriter.publishOnChainTransactionReceived(
                "tx-hash:2",
                new OnChainTransactionReceivedEvent("user-123", 3_000L, "tx-hash", 2L)
        );
        outboxWriter.publishInternalTransferInitiated(
                "tx-internal",
                new InternalTransferInitiatedEvent("tx-internal", "sender-123", "receiver-123", 1_500L, "internal-payment-hash")
        );
        outboxEventRepository.flush();

        List<OutboxEventEntity> events = outboxEventRepository.findAll();
        assertEquals(4, events.size());
        assertOutboxEvent(events, "Invoice", "invoice-123", KafkaTopics.INVOICE_SETTLED.getCode(), "\"paymentHash\":\"payment-hash\"");
        assertOutboxEvent(events, "ONCHAIN_PAYMENT", "tx-onchain", KafkaTopics.ONCHAIN_PAYMENT_INITIATED.getCode(), "\"address\":\"bc1qrecipient\"");
        assertOutboxEvent(events, "ONCHAIN_TRANSACTION", "tx-hash:2", KafkaTopics.ONCHAIN_TRANSACTION_RECEIVED.getCode(), "\"outputIndex\":2");
        assertOutboxEvent(events, "INTERNAL_TRANSFER", "tx-internal", KafkaTopics.INTERNAL_TRANSFER_INITIATED.getCode(), "\"receiverId\":\"receiver-123\"");
        assertTrue(events.stream().allMatch(event -> event.getProcessedAt() == null));
        assertTrue(events.stream().allMatch(event -> event.getCreatedAt() != null));
    }

    private void assertOutboxEvent(
            List<OutboxEventEntity> events,
            String aggregateType,
            String aggregateId,
            String eventType,
            String payloadFragment
    ) {
        assertTrue(events.stream().anyMatch(event ->
                        aggregateType.equals(event.getAggregateType())
                                && aggregateId.equals(event.getAggregateId())
                                && eventType.equals(event.getEventType())
                                && event.getPayload().contains(payloadFragment)
                ),
                "Expected outbox event " + eventType + " for aggregate " + aggregateId
        );
    }
}
