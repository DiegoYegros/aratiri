package com.aratiri.infrastructure.messaging;

import com.aratiri.errors.ApplicationException;
import com.aratiri.payments.application.dto.PayInvoiceRequestDTO;
import com.aratiri.payments.application.event.PaymentInitiatedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventPublisherTest {

    private static final String TOPIC = "payment.sent";
    private static final String KEY = "tx-123";

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private KafkaEventPublisher publisher;
    private final JsonMapper jsonMapper = new JsonMapper();

    @BeforeEach
    void setUp() {
        publisher = new KafkaEventPublisher(kafkaTemplate, jsonMapper);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void publish_outsideTransaction_sendsImmediately() {
        when(kafkaTemplate.send(eq(TOPIC), eq(KEY), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publish(TOPIC, KEY, payload());

        verify(kafkaTemplate).send(eq(TOPIC), eq(KEY), anyString());
    }

    @Test
    void publish_insideActiveTransaction_defersSendToAfterCommit() {
        when(kafkaTemplate.send(eq(TOPIC), eq(KEY), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        TransactionSynchronizationManager.initSynchronization();

        publisher.publish(TOPIC, KEY, payload());

        verify(kafkaTemplate, never()).send(eq(TOPIC), eq(KEY), anyString());
        assertEquals(1, TransactionSynchronizationManager.getSynchronizations().size(),
                "exactly one afterCommit synchronization must be registered");

        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }

        verify(kafkaTemplate).send(eq(TOPIC), eq(KEY), anyString());
    }

    @Test
    void publish_insideActiveTransaction_rollbackNeverSends() {
        TransactionSynchronizationManager.initSynchronization();

        publisher.publish(TOPIC, KEY, payload());

        verify(kafkaTemplate, never()).send(eq(TOPIC), eq(KEY), anyString());
    }

    @Test
    void publish_sendFailure_isLoggedAndDoesNotThrow() {
        when(kafkaTemplate.send(eq(TOPIC), eq(KEY), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("kafka down")));

        publisher.publish(TOPIC, KEY, payload());

        verify(kafkaTemplate).send(eq(TOPIC), eq(KEY), anyString());
    }

    @Test
    void publish_synchronousSendFailure_isLoggedAndDoesNotThrow() {
        when(kafkaTemplate.send(eq(TOPIC), eq(KEY), anyString()))
                .thenThrow(new RuntimeException("producer closed"));

        publisher.publish(TOPIC, KEY, payload());

        verify(kafkaTemplate).send(eq(TOPIC), eq(KEY), anyString());
    }

    @Test
    void publish_serializationFailure_throwsBeforeAnySend() {
        BrokenPayload payload = new BrokenPayload();
        assertThrows(ApplicationException.class,
                () -> publisher.publish(TOPIC, KEY, payload));

        verify(kafkaTemplate, never()).send(eq(TOPIC), eq(KEY), anyString());
    }

    @Test
    void publish_serializedPayload_keepsConsumerJsonShape() throws Exception {
        when(kafkaTemplate.send(eq(TOPIC), eq(KEY), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publish(TOPIC, KEY, payload());

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(TOPIC), eq(KEY), payloadCaptor.capture());
        String serialized = payloadCaptor.getValue();

        assertTrue(serialized.contains("\"external_reference\":\"external-ref-123\""),
                "payload must keep the consumer-facing external_reference key");
        PaymentInitiatedEvent roundTripped = jsonMapper.readValue(serialized, PaymentInitiatedEvent.class);
        PayInvoiceRequestDTO request = roundTripped.getPayRequest();
        assertEquals("user-123", roundTripped.userId());
        assertEquals("tx-123", roundTripped.transactionId());
        assertEquals("lnbc1paymentrequest", request.getInvoice());
        assertEquals(25L, request.getFeeLimitSat());
        assertEquals(30, request.getTimeoutSeconds());
        assertEquals("external-ref-123", request.getExternalReference());
        assertEquals("{\"orderId\":\"order-123\"}", request.getMetadata());
    }

    private static PaymentInitiatedEvent payload() {
        PayInvoiceRequestDTO request = new PayInvoiceRequestDTO();
        request.setInvoice("lnbc1paymentrequest");
        request.setFeeLimitSat(25L);
        request.setTimeoutSeconds(30);
        request.setExternalReference("external-ref-123");
        request.setMetadata("{\"orderId\":\"order-123\"}");
        return new PaymentInitiatedEvent("user-123", "tx-123", request);
    }

    private static class BrokenPayload {
        public String getValue() {
            throw new IllegalStateException("broken getter");
        }
    }
}
