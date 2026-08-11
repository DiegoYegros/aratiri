package com.aratiri.webhooks.infrastructure;

import com.aratiri.infrastructure.messaging.EventPublisher;
import com.aratiri.infrastructure.messaging.KafkaTopicNames;
import com.aratiri.webhooks.application.dto.WebhookPayload;
import com.aratiri.webhooks.application.dto.WebhookPayloadData;
import com.aratiri.webhooks.application.event.WebhookEventCreatedEvent;
import com.aratiri.webhooks.application.port.out.WebhookEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

/**
 * Serializes the full webhook payload envelope (id, type, createdAt, apiVersion, data) at
 * publish time and sends it as a string on {@code webhook.event.created} so the consumer
 * reconstructs the exact {@code webhook_events.payload} column value.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaWebhookEventPublisher implements WebhookEventPublisher {

    private static final String API_VERSION = "2026-04-25";

    private final EventPublisher eventPublisher;
    private final JsonMapper jsonMapper;

    @Override
    public void publishWebhookEvent(
            String eventKey,
            String eventType,
            String aggregateType,
            String aggregateId,
            String userId,
            String externalReference,
            WebhookPayloadData data
    ) {
        // Single id for both the envelope and the record so the consumer can reuse it as the
        // webhook_events row id — the delivered payload.id stays correlated with the stored row.
        UUID eventId = UUID.randomUUID();
        WebhookPayload payload = WebhookPayload.builder()
                .id(eventId.toString())
                .type(eventType)
                .createdAt(Instant.now())
                .apiVersion(API_VERSION)
                .data(data)
                .build();
        String payloadJson;
        try {
            payloadJson = jsonMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.error("Failed to serialize webhook payload for eventType={} eventKey={}", eventType, eventKey, e);
            return;
        }
        eventPublisher.publish(
                KafkaTopicNames.WEBHOOK_EVENT_CREATED,
                eventKey,
                new WebhookEventCreatedEvent(
                        eventId.toString(),
                        eventKey,
                        eventType,
                        aggregateType,
                        aggregateId,
                        userId,
                        externalReference,
                        payloadJson
                )
        );
    }
}
