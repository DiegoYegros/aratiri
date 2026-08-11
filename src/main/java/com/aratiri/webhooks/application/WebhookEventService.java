package com.aratiri.webhooks.application;

import com.aratiri.infrastructure.persistence.jpa.entity.WebhookDeliveryEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.WebhookDeliveryStatus;
import com.aratiri.infrastructure.persistence.jpa.entity.WebhookEndpointEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.WebhookEventEntity;
import com.aratiri.infrastructure.persistence.jpa.repository.WebhookDeliveryRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.WebhookEndpointRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.WebhookEventRepository;
import com.aratiri.webhooks.application.dto.WebhookPayload;
import com.aratiri.webhooks.application.dto.WebhookPayloadData;
import com.aratiri.webhooks.application.event.WebhookEventCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookEventService {

    private static final String API_VERSION = "2026-04-25";

    private final WebhookEndpointRepository webhookEndpointRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final WebhookDeliveryRepository webhookDeliveryRepository;
    private final JsonMapper jsonMapper;

    public void createWebhookTestEvent(WebhookTestEventFacts facts) {
        String eventType = "webhook.test";
        String eventKey = eventType + ":" + facts.endpointId() + ":" + UUID.randomUUID();
        WebhookPayloadData data = WebhookPayloadData.builder()
                .userId("system")
                .build();
        WebhookEventEntity event = createEventEntity(eventKey, eventType, "WEBHOOK", facts.endpointId().toString(), null, null, data);
        if (event == null) {
            return;
        }
        WebhookDeliveryEntity delivery = WebhookDeliveryEntity.builder()
                .eventId(event.getId())
                .endpointId(facts.endpointId())
                .eventType(eventType)
                .payload(event.getPayload())
                .status(WebhookDeliveryStatus.PENDING)
                .attemptCount(0)
                .nextAttemptAt(Instant.now())
                .build();
        webhookDeliveryRepository.save(delivery);
        log.debug("Created test webhook delivery for event={} endpoint={}", event.getId(), facts.endpointId());
    }

    /**
     * Creates the {@code webhook_events} row (idempotent via the unique {@code event_key})
     * and fans out one {@code webhook_deliveries} row per enabled+subscribed endpoint.
     * {@code event.eventId()} is reused as both the {@code webhook_events} row id and the envelope
     * {@code payload.id}, so delivered payloads correlate with the stored row. {@code payload} is
     * stored verbatim as the payload column. Note the row's {@code created_at} is the insert time
     * (Hibernate {@code @CreationTimestamp}); the envelope's {@code created_at} is the publish
     * time, so they match in the happy path and diverge under consumer retry/delay.
     */
    public void createEventAndDeliveries(WebhookEventCreatedEvent event) {
        WebhookEventEntity entity = createEventEntity(event);
        if (entity == null) {
            return;
        }

        List<WebhookEndpointEntity> endpoints = webhookEndpointRepository.findAllEnabledWithSubscriptions();
        for (WebhookEndpointEntity endpoint : endpoints) {
            boolean subscribed = endpoint.getSubscriptions().stream()
                    .anyMatch(sub -> sub.getEventType().equals(event.eventType()));
            if (subscribed) {
                WebhookDeliveryEntity delivery = WebhookDeliveryEntity.builder()
                        .eventId(entity.getId())
                        .endpointId(endpoint.getId())
                        .eventType(event.eventType())
                        .payload(entity.getPayload())
                        .status(WebhookDeliveryStatus.PENDING)
                        .attemptCount(0)
                        .nextAttemptAt(Instant.now())
                        .build();
                webhookDeliveryRepository.save(delivery);
                log.debug("Created webhook delivery for event={} endpoint={}", entity.getId(), endpoint.getId());
            }
        }
    }

    private WebhookEventEntity createEventEntity(WebhookEventCreatedEvent event) {
        Optional<WebhookEventEntity> existing = webhookEventRepository.findByEventKey(event.eventKey());
        if (existing.isPresent()) {
            log.debug("Webhook event already exists for key: {}", event.eventKey());
            return null;
        }

        WebhookEventEntity entity = WebhookEventEntity.builder()
                .id(UUID.fromString(event.eventId()))
                .eventKey(event.eventKey())
                .eventType(event.eventType())
                .aggregateType(event.aggregateType())
                .aggregateId(event.aggregateId())
                .userId(event.userId())
                .externalReference(event.externalReference())
                .payload(event.payload())
                .build();
        try {
            webhookEventRepository.save(entity);
        } catch (DataIntegrityViolationException _) {
            log.debug("Webhook event duplicate key prevented by constraint: {}", event.eventKey());
            return null;
        }
        return entity;
    }

    private WebhookEventEntity createEventEntity(String eventKey, String eventType, String aggregateType, String aggregateId, String userId, String externalReference, WebhookPayloadData data) {
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
            log.error("Failed to serialize webhook payload for eventType={}", eventType, e);
            return null;
        }
        return createEventEntity(new WebhookEventCreatedEvent(
                eventId.toString(),
                eventKey,
                eventType,
                aggregateType,
                aggregateId,
                userId,
                externalReference,
                payloadJson
        ));
    }
}
