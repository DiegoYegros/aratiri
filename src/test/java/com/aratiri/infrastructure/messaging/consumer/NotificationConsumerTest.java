package com.aratiri.infrastructure.messaging.consumer;

import com.aratiri.auth.application.port.out.NotificationPort;
import com.aratiri.infrastructure.messaging.KafkaTopicNames;
import com.aratiri.payments.application.event.PaymentSentEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationConsumerTest {

    @Mock
    private NotificationPort notificationsService;

    @Mock
    private JsonMapper jsonMapper;

    @Mock
    private Acknowledgment acknowledgment;

    private NotificationConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new NotificationConsumer(notificationsService, jsonMapper);
    }

    @Test
    void handlePaymentSent_withNullPaymentHash_keepsPaymentRequestKeyAndAcknowledges() throws Exception {
        String message = "{\"userId\":\"user-1\",\"transactionId\":\"tx-1\",\"amount\":25000,\"paymentHash\":null}";
        PaymentSentEvent event = new PaymentSentEvent(
                "user-1",
                "tx-1",
                25_000L,
                null,
                LocalDateTime.now(),
                "On-chain payment to tb1qdestination"
        );

        when(jsonMapper.readValue(message, PaymentSentEvent.class)).thenReturn(event);

        consumer.handlePaymentSettledForNotification(message, KafkaTopicNames.PAYMENT_SENT, acknowledgment);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(notificationsService).sendNotification(eq("user-1"), eq("payment_sent"), payloadCaptor.capture());
        Map<String, Object> payload = payloadCaptor.getValue();
        assertEquals("Payment Sent", payload.get("message"));
        assertEquals("tx-1", payload.get("transactionId"));
        assertEquals(25_000L, payload.get("amountSats"));
        assertTrue(payload.containsKey("paymentRequest"));
        assertNull(payload.get("paymentRequest"));
        assertEquals("On-chain payment to tb1qdestination", payload.get("memo"));
        verify(acknowledgment).acknowledge();
    }

    @Test
    void handlePaymentSent_withPaymentHash_sendsPayloadAndAcknowledges() throws Exception {
        String message = "{\"userId\":\"user-1\",\"transactionId\":\"tx-2\",\"amount\":2100,\"paymentHash\":\"abc123\"}";
        PaymentSentEvent event = new PaymentSentEvent(
                "user-1",
                "tx-2",
                2100L,
                "abc123",
                LocalDateTime.now(),
                "Lightning payment"
        );

        when(jsonMapper.readValue(message, PaymentSentEvent.class)).thenReturn(event);

        consumer.handlePaymentSettledForNotification(message, KafkaTopicNames.PAYMENT_SENT, acknowledgment);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(notificationsService).sendNotification(eq("user-1"), eq("payment_sent"), payloadCaptor.capture());
        Map<String, Object> payload = payloadCaptor.getValue();
        assertEquals("Payment Sent", payload.get("message"));
        assertEquals("tx-2", payload.get("transactionId"));
        assertEquals(2100L, payload.get("amountSats"));
        assertEquals("abc123", payload.get("paymentRequest"));
        assertEquals("Lightning payment", payload.get("memo"));
        verify(acknowledgment).acknowledge();
    }

    @Test
    void handlePaymentSent_malformedJson_doesNotAcknowledge() throws Exception {
        String message = "{not-json";
        when(jsonMapper.readValue(message, PaymentSentEvent.class))
                .thenThrow(new IllegalArgumentException("bad payload"));

        assertThrows(
                IllegalStateException.class,
                () -> consumer.handlePaymentSettledForNotification(message, KafkaTopicNames.PAYMENT_SENT, acknowledgment)
        );

        verify(notificationsService, never()).sendNotification(anyString(), anyString(), any());
        verify(acknowledgment, never()).acknowledge();
    }
}
