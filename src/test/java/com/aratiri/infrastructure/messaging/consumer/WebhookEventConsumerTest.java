package com.aratiri.infrastructure.messaging.consumer;

import com.aratiri.infrastructure.messaging.KafkaTopicNames;
import com.aratiri.webhooks.application.WebhookEventService;
import com.aratiri.webhooks.application.event.WebhookEventCreatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebhookEventConsumerTest {

    @Mock
    private WebhookEventService webhookEventService;

    @Mock
    private JsonMapper jsonMapper;

    @Mock
    private Acknowledgment acknowledgment;

    private WebhookEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new WebhookEventConsumer(webhookEventService, jsonMapper);
    }

    @Test
    void handleWebhookEventCreated_deserializesAndCallsServiceThenAcknowledges() throws Exception {
        WebhookEventCreatedEvent event = new WebhookEventCreatedEvent(
                "6f9619ff-8b86-d011-b42d-00cf4fc964ff",
                "payment.succeeded:tx-1",
                "payment.succeeded",
                "TRANSACTION",
                "tx-1",
                "user-1",
                "external-1",
                "{\"id\":\"evt-1\"}"
        );
        when(jsonMapper.readValue("{msg}", WebhookEventCreatedEvent.class)).thenReturn(event);

        consumer.handleWebhookEventCreated("{msg}", KafkaTopicNames.WEBHOOK_EVENT_CREATED, 0, 0L, acknowledgment);

        verify(webhookEventService).createEventAndDeliveries(same(event));
        verify(acknowledgment).acknowledge();
    }

    @Test
    void handleWebhookEventCreated_redelivery_isAcknowledgedAndServiceDedupes() throws Exception {
        WebhookEventCreatedEvent event = new WebhookEventCreatedEvent(
                "6f9619ff-8b86-d011-b42d-00cf4fc964ff",
                "invoice.created:hash-1",
                "invoice.created",
                "INVOICE",
                "inv-1",
                "user-1",
                null,
                "{\"id\":\"evt-1\"}"
        );
        when(jsonMapper.readValue("{msg}", WebhookEventCreatedEvent.class)).thenReturn(event);

        consumer.handleWebhookEventCreated("{msg}", KafkaTopicNames.WEBHOOK_EVENT_CREATED, 0, 0L, acknowledgment);
        consumer.handleWebhookEventCreated("{msg}", KafkaTopicNames.WEBHOOK_EVENT_CREATED, 0, 0L, acknowledgment);

        verify(webhookEventService, org.mockito.Mockito.times(2)).createEventAndDeliveries(same(event));
        verify(acknowledgment, org.mockito.Mockito.times(2)).acknowledge();
    }

    @Test
    void handleWebhookEventCreated_malformedJson_throwsWithoutAcknowledging() throws Exception {
        when(jsonMapper.readValue("{poison", WebhookEventCreatedEvent.class))
                .thenThrow(new IllegalArgumentException("bad payload"));

        assertThrows(
                IllegalArgumentException.class,
                () -> consumer.handleWebhookEventCreated("{poison", KafkaTopicNames.WEBHOOK_EVENT_CREATED, 0, 0L, acknowledgment)
        );

        verify(webhookEventService, never()).createEventAndDeliveries(any());
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void handleFailedWebhookEventCreation_logsWithoutRethrowing() {
        assertDoesNotThrow(() -> consumer.handleFailedWebhookEventCreation(
                "{poison", KafkaTopicNames.WEBHOOK_EVENT_CREATED, "deserialization failed"));
    }
}
