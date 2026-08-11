package com.aratiri.webhooks.infrastructure;

import com.aratiri.infrastructure.messaging.EventPublisher;
import com.aratiri.infrastructure.messaging.KafkaTopicNames;
import com.aratiri.webhooks.application.dto.WebhookPayload;
import com.aratiri.webhooks.application.dto.WebhookPayloadData;
import com.aratiri.webhooks.application.event.WebhookEventCreatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class KafkaWebhookEventPublisherTest {

    @Mock
    private EventPublisher eventPublisher;

    private final JsonMapper jsonMapper = new JsonMapper();
    private KafkaWebhookEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new KafkaWebhookEventPublisher(eventPublisher, jsonMapper);
    }

    @Test
    void publishWebhookEvent_sendsEventWithSerializedPayloadEnvelope() throws Exception {
        WebhookPayloadData data = WebhookPayloadData.builder()
                .transactionId("tx-1")
                .userId("user-1")
                .externalReference("external-1")
                .metadata("{\"order\":\"123\"}")
                .amountSat(1000L)
                .status("PENDING")
                .referenceId("ref-1")
                .build();

        publisher.publishWebhookEvent(
                "payment.accepted:tx-1",
                "payment.accepted",
                "TRANSACTION",
                "tx-1",
                "user-1",
                "external-1",
                data
        );

        ArgumentCaptor<WebhookEventCreatedEvent> captor = ArgumentCaptor.forClass(WebhookEventCreatedEvent.class);
        verify(eventPublisher).publish(eq(KafkaTopicNames.WEBHOOK_EVENT_CREATED), eq("payment.accepted:tx-1"), captor.capture());

        WebhookEventCreatedEvent event = captor.getValue();
        assertEquals("payment.accepted:tx-1", event.eventKey());
        assertEquals("payment.accepted", event.eventType());
        assertEquals("TRANSACTION", event.aggregateType());
        assertEquals("tx-1", event.aggregateId());
        assertEquals("user-1", event.userId());
        assertEquals("external-1", event.externalReference());

        // The payload string must round-trip to the exact webhook envelope shape the consumer stores.
        WebhookPayload payload = jsonMapper.readValue(event.payload(), WebhookPayload.class);
        assertNotNull(payload.getId());
        assertEquals(payload.getId(), event.eventId(),
                "record eventId must be the envelope id so the consumer can reuse it as the row id");
        assertEquals("payment.accepted", payload.getType());
        assertNotNull(payload.getCreatedAt());
        assertEquals("2026-04-25", payload.getApiVersion());
        assertNotNull(payload.getData());
        assertEquals("tx-1", payload.getData().getTransactionId());
        assertEquals("user-1", payload.getData().getUserId());
        assertEquals("external-1", payload.getData().getExternalReference());
        assertEquals("{\"order\":\"123\"}", payload.getData().getMetadata());
        assertEquals(1000L, payload.getData().getAmountSat());
        assertEquals("PENDING", payload.getData().getStatus());
        assertEquals("ref-1", payload.getData().getReferenceId());
    }

    @Test
    void publishWebhookEvent_payloadKeepsSnakeCaseKeysForWireConsumers() {
        WebhookPayloadData data = WebhookPayloadData.builder()
                .transactionId("tx-1")
                .userId("user-1")
                .amountSat(1000L)
                .build();

        publisher.publishWebhookEvent(
                "invoice.created:hash-1",
                "invoice.created",
                "INVOICE",
                "inv-1",
                "user-1",
                null,
                data
        );

        ArgumentCaptor<WebhookEventCreatedEvent> captor = ArgumentCaptor.forClass(WebhookEventCreatedEvent.class);
        verify(eventPublisher).publish(eq(KafkaTopicNames.WEBHOOK_EVENT_CREATED), eq("invoice.created:hash-1"), captor.capture());
        String payloadJson = captor.getValue().payload();
        assertTrue(payloadJson.contains("\"transaction_id\":\"tx-1\""),
                "payload must carry snake_case transaction_id, was: " + payloadJson);
        assertTrue(payloadJson.contains("\"amount_sat\":1000"),
                "payload must carry snake_case amount_sat, was: " + payloadJson);
    }
}
