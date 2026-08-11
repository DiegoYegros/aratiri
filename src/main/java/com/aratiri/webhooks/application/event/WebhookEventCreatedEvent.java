package com.aratiri.webhooks.application.event;

/**
 * Kafka record for {@code webhook.event.created}. Carries the already-serialized webhook
 * payload so the consumer can rebuild the {@code webhook_events} row without DB lookups.
 * {@code eventId} is the envelope id, reused as the {@code webhook_events} row id so the
 * delivered {@code payload.id} correlates with the stored row.
 */
public record WebhookEventCreatedEvent(
        String eventId,
        String eventKey,
        String eventType,
        String aggregateType,
        String aggregateId,
        String userId,
        String externalReference,
        String payload
) {
}
