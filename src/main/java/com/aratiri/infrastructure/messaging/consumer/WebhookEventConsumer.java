package com.aratiri.infrastructure.messaging.consumer;

import com.aratiri.infrastructure.messaging.KafkaTopicNames;
import com.aratiri.webhooks.application.WebhookEventService;
import com.aratiri.webhooks.application.event.WebhookEventCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consumes {@code webhook.event.created} and rebuilds the {@code webhook_events} row plus
 * deliveries via {@link WebhookEventService#createEventAndDeliveries}, which dedupes by
 * {@code event_key}. After retry exhaustion the event is logged and lost (documented risk);
 * the deduping idempotency makes redelivery safe.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebhookEventConsumer {

    private final WebhookEventService webhookEventService;
    private final JsonMapper jsonMapper;

    @KafkaListener(topics = KafkaTopicNames.WEBHOOK_EVENT_CREATED,
            groupId = "${aratiri.kafka.groups.webhook-event:webhook-event-group}")
    @RetryableTopic(
            attempts = "5",
            backOff = @BackOff(delay = 1000, multiplier = 2.0),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
            include = {Exception.class}
    )
    public void handleWebhookEventCreated(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        log.info("Creating webhook event from topic: {}, partition: {}, offset: {}", topic, partition, offset);
        try {
            WebhookEventCreatedEvent event = jsonMapper.readValue(message, WebhookEventCreatedEvent.class);
            webhookEventService.createEventAndDeliveries(event);
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Webhook event creation failed for message: {}", message, e);
            throw e;
        }
    }

    @DltHandler
    public void handleFailedWebhookEventCreation(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = "kafka_exception-message", required = false) String exceptionMessage) {
        log.error("Webhook event creation failed after all retries and is lost. Topic: {}, Message: {}, Error: {}",
                topic, message, exceptionMessage);
    }
}
