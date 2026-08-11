package com.aratiri.infrastructure.messaging.consumer;

import com.aratiri.auth.application.port.out.NotificationPort;
import com.aratiri.infrastructure.messaging.KafkaTopicNames;
import com.aratiri.invoices.application.event.InvoiceSettledEvent;
import com.aratiri.payments.application.event.PaymentSentEvent;
import com.aratiri.transactions.application.event.InternalTransferCompletedEvent;
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
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

    @Test
    void duplicatePaymentSent_withinDedupWindow_skipsSecondPush() throws Exception {
        String message = "{\"userId\":\"user-1\",\"transactionId\":\"tx-1\",\"amount\":25000,\"paymentHash\":null}";
        PaymentSentEvent event = new PaymentSentEvent(
                "user-1", "tx-1", 25_000L, null, LocalDateTime.now(), "On-chain payment");
        when(jsonMapper.readValue(message, PaymentSentEvent.class)).thenReturn(event);

        consumer.handlePaymentSettledForNotification(message, KafkaTopicNames.PAYMENT_SENT, acknowledgment);
        consumer.handlePaymentSettledForNotification(message, KafkaTopicNames.PAYMENT_SENT, acknowledgment);

        verify(notificationsService, times(1)).sendNotification(anyString(), anyString(), any());
        verify(acknowledgment, times(2)).acknowledge();
    }

    @Test
    void duplicateInvoiceSettled_withinDedupWindow_skipsSecondPush() throws Exception {
        String message = "{\"userId\":\"user-1\",\"paymentHash\":\"payhash-1\",\"amount\":1000}";
        InvoiceSettledEvent event = new InvoiceSettledEvent(
                "user-1", 1000L, "payhash-1", LocalDateTime.now(), "memo");
        when(jsonMapper.readValue(message, InvoiceSettledEvent.class)).thenReturn(event);

        consumer.handlePaymentSettledForNotification(message, KafkaTopicNames.INVOICE_SETTLED, acknowledgment);
        consumer.handlePaymentSettledForNotification(message, KafkaTopicNames.INVOICE_SETTLED, acknowledgment);

        verify(notificationsService, times(1)).sendNotification(anyString(), anyString(), any());
        verify(acknowledgment, times(2)).acknowledge();
    }

    @Test
    void duplicateInternalTransferCompleted_skipsReceiverAndSenderPushes() throws Exception {
        String message = "{\"senderId\":\"sender-1\",\"receiverId\":\"receiver-1\",\"paymentHash\":\"payhash-2\",\"amountSat\":500}";
        InternalTransferCompletedEvent event = new InternalTransferCompletedEvent(
                "sender-1", "receiver-1", 500L, "payhash-2", LocalDateTime.now(), "memo");
        when(jsonMapper.readValue(message, InternalTransferCompletedEvent.class)).thenReturn(event);

        consumer.handlePaymentSettledForNotification(message, KafkaTopicNames.INTERNAL_TRANSFER_COMPLETED, acknowledgment);
        consumer.handlePaymentSettledForNotification(message, KafkaTopicNames.INTERNAL_TRANSFER_COMPLETED, acknowledgment);

        verify(notificationsService, times(1)).sendNotification(eq("receiver-1"), anyString(), any());
        verify(notificationsService, times(1)).sendNotification(eq("sender-1"), anyString(), any());
        verify(acknowledgment, times(2)).acknowledge();
    }

    @Test
    void dedupExpiry_allowsRepush() throws Exception {
        NotificationConsumer ttlConsumer = new NotificationConsumer(
                notificationsService, jsonMapper, java.time.Duration.ofMillis(50));
        String message = "{\"userId\":\"user-1\",\"transactionId\":\"tx-3\",\"amount\":1000,\"paymentHash\":null}";
        PaymentSentEvent event = new PaymentSentEvent(
                "user-1", "tx-3", 1000L, null, LocalDateTime.now(), "memo");
        when(jsonMapper.readValue(message, PaymentSentEvent.class)).thenReturn(event);

        ttlConsumer.handlePaymentSettledForNotification(message, KafkaTopicNames.PAYMENT_SENT, acknowledgment);
        verify(notificationsService, times(1)).sendNotification(anyString(), anyString(), any());

        // Caffeine expires entries lazily on access, so re-publish until the dedup entry has aged
        // out; the first delivery after expiry is the second push. Polling instead of parking on a
        // fixed TTL keeps the test deterministic under build load.
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            ttlConsumer.handlePaymentSettledForNotification(message, KafkaTopicNames.PAYMENT_SENT, acknowledgment);
            try {
                verify(notificationsService, times(2)).sendNotification(anyString(), anyString(), any());
                return;
            } catch (AssertionError _) {
                // retry after the TTL window elapses
            }
        }
        fail("dedup entry never expired to allow a repush within 10s");
    }
}
