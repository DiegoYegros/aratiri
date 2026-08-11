package com.aratiri.webhooks.application.port.out;

import com.aratiri.webhooks.application.dto.WebhookPayloadData;

/**
 * Publishes webhook event creation work to Kafka. The consumer rebuilds the
 * {@code webhook_events} row (and fans out deliveries) with the exact payload
 * carried in the record, so business transactions no longer write webhook rows inline.
 */
public interface WebhookEventPublisher {

    void publishWebhookEvent(
            String eventKey,
            String eventType,
            String aggregateType,
            String aggregateId,
            String userId,
            String externalReference,
            WebhookPayloadData data
    );
}
