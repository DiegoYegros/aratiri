package com.aratiri.webhooks.application;

import com.aratiri.infrastructure.persistence.jpa.entity.*;
import com.aratiri.infrastructure.persistence.jpa.repository.WebhookDeliveryRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.WebhookEndpointRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.WebhookEventRepository;
import com.aratiri.webhooks.application.dto.WebhookPayload;
import com.aratiri.webhooks.application.event.WebhookEventCreatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookEventServiceTest {

    private static final String PAYLOAD_JSON = "{\"id\":\"evt-1\",\"type\":\"payment.accepted\",\"data\":{}}";
    private static final String EVENT_ID = "6f9619ff-8b86-d011-b42d-00cf4fc964ff";

    @Mock
    private WebhookEndpointRepository webhookEndpointRepository;

    @Mock
    private WebhookEventRepository webhookEventRepository;

    @Mock
    private WebhookDeliveryRepository webhookDeliveryRepository;

    @Mock
    private JsonMapper jsonMapper;

    private WebhookEventService webhookEventService;

    @BeforeEach
    void setUp() {
        webhookEventService = new WebhookEventService(webhookEndpointRepository, webhookEventRepository, webhookDeliveryRepository, jsonMapper);
    }

    @Test
    void createEventAndDeliveries_storesPayloadVerbatimAndCreatesDeliveryForEnabledEndpoint() {
        WebhookEndpointEntity endpoint = enabledEndpoint("payment.accepted");
        when(webhookEndpointRepository.findAllEnabledWithSubscriptions()).thenReturn(List.of(endpoint));
        when(webhookEventRepository.findByEventKey("payment.accepted:tx-1")).thenReturn(Optional.empty());

        webhookEventService.createEventAndDeliveries(event(
                EVENT_ID,
                "payment.accepted:tx-1",
                "payment.accepted",
                "TRANSACTION",
                "tx-1",
                "user-1",
                "external-1",
                PAYLOAD_JSON
        ));

        ArgumentCaptor<WebhookEventEntity> eventCaptor = ArgumentCaptor.forClass(WebhookEventEntity.class);
        verify(webhookEventRepository).save(eventCaptor.capture());
        assertEquals(EVENT_ID, eventCaptor.getValue().getId().toString(),
                "row id must be the envelope id so payload.id correlates with webhook_events.id");
        assertEquals("payment.accepted:tx-1", eventCaptor.getValue().getEventKey());
        assertEquals("payment.accepted", eventCaptor.getValue().getEventType());
        assertEquals("TRANSACTION", eventCaptor.getValue().getAggregateType());
        assertEquals("tx-1", eventCaptor.getValue().getAggregateId());
        assertEquals("user-1", eventCaptor.getValue().getUserId());
        assertEquals("external-1", eventCaptor.getValue().getExternalReference());
        assertEquals(PAYLOAD_JSON, eventCaptor.getValue().getPayload(), "payload must be stored verbatim");

        ArgumentCaptor<WebhookDeliveryEntity> deliveryCaptor = ArgumentCaptor.forClass(WebhookDeliveryEntity.class);
        verify(webhookDeliveryRepository).save(deliveryCaptor.capture());
        assertEquals(endpoint.getId(), deliveryCaptor.getValue().getEndpointId());
        assertEquals("payment.accepted", deliveryCaptor.getValue().getEventType());
        assertEquals(PAYLOAD_JSON, deliveryCaptor.getValue().getPayload());
        assertEquals(WebhookDeliveryStatus.PENDING, deliveryCaptor.getValue().getStatus());
        assertEquals(0, deliveryCaptor.getValue().getAttemptCount());
        assertNotNull(deliveryCaptor.getValue().getNextAttemptAt());
    }

    @Test
    void createEventAndDeliveries_noDeliveryWhenNoEndpointIsSubscribed() {
        when(webhookEndpointRepository.findAllEnabledWithSubscriptions()).thenReturn(List.of());
        when(webhookEventRepository.findByEventKey("payment.accepted:tx-1")).thenReturn(Optional.empty());

        webhookEventService.createEventAndDeliveries(event(
                EVENT_ID, "payment.accepted:tx-1", "payment.accepted", "TRANSACTION", "tx-1", "user-1", null, PAYLOAD_JSON));

        verify(webhookEventRepository).save(any());
        verify(webhookDeliveryRepository, never()).save(any());
    }

    @Test
    void createEventAndDeliveries_onlySubscribedEndpointsReceiveDeliveries() {
        WebhookEndpointEntity subscribed = enabledEndpoint("payment.accepted");
        WebhookEndpointEntity other = enabledEndpoint("payment.succeeded");
        when(webhookEndpointRepository.findAllEnabledWithSubscriptions()).thenReturn(List.of(subscribed, other));
        when(webhookEventRepository.findByEventKey("payment.accepted:tx-1")).thenReturn(Optional.empty());

        webhookEventService.createEventAndDeliveries(event(
                EVENT_ID, "payment.accepted:tx-1", "payment.accepted", "TRANSACTION", "tx-1", "user-1", null, PAYLOAD_JSON));

        ArgumentCaptor<WebhookDeliveryEntity> deliveryCaptor = ArgumentCaptor.forClass(WebhookDeliveryEntity.class);
        verify(webhookDeliveryRepository).save(deliveryCaptor.capture());
        assertEquals(subscribed.getId(), deliveryCaptor.getValue().getEndpointId());
    }

    @Test
    void createEventAndDeliveries_idempotentByEventKey() {
        WebhookEventEntity existing = WebhookEventEntity.builder().eventKey("payment.accepted:tx-1").build();
        when(webhookEventRepository.findByEventKey("payment.accepted:tx-1")).thenReturn(Optional.of(existing));

        webhookEventService.createEventAndDeliveries(event(
                EVENT_ID, "payment.accepted:tx-1", "payment.accepted", "TRANSACTION", "tx-1", "user-1", null, PAYLOAD_JSON));

        verify(webhookEventRepository, never()).save(any());
        verify(webhookDeliveryRepository, never()).save(any());
    }

    @Test
    void createEventAndDeliveries_duplicateKeyViolationIsSwallowed() {
        when(webhookEventRepository.findByEventKey("payment.accepted:tx-1")).thenReturn(Optional.empty());
        when(webhookEventRepository.save(any()))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate key"));

        assertDoesNotThrow(() -> webhookEventService.createEventAndDeliveries(event(
                EVENT_ID, "payment.accepted:tx-1", "payment.accepted", "TRANSACTION", "tx-1", "user-1", null, PAYLOAD_JSON)));

        verify(webhookDeliveryRepository, never()).save(any());
    }

    @Test
    void createWebhookTestEvent_createsEventAndDeliveryForTheEndpoint() throws Exception {
        WebhookEndpointEntity endpoint = enabledEndpoint("webhook.test");
        when(webhookEventRepository.findByEventKey(any())).thenReturn(Optional.empty());
        when(jsonMapper.writeValueAsString(any())).thenReturn(PAYLOAD_JSON);

        webhookEventService.createWebhookTestEvent(new WebhookTestEventFacts(endpoint.getId()));

        ArgumentCaptor<WebhookEventEntity> eventCaptor = ArgumentCaptor.forClass(WebhookEventEntity.class);
        verify(webhookEventRepository).save(eventCaptor.capture());
        assertEquals("webhook.test", eventCaptor.getValue().getEventType());
        assertEquals("WEBHOOK", eventCaptor.getValue().getAggregateType());
        assertEquals(endpoint.getId().toString(), eventCaptor.getValue().getAggregateId());
        assertNull(eventCaptor.getValue().getUserId());
        assertTrue(eventCaptor.getValue().getEventKey().startsWith("webhook.test:" + endpoint.getId() + ":"));

        ArgumentCaptor<WebhookPayload> payloadCaptor = ArgumentCaptor.forClass(WebhookPayload.class);
        verify(jsonMapper).writeValueAsString(payloadCaptor.capture());
        assertEquals("webhook.test", payloadCaptor.getValue().getType());
        assertEquals("system", payloadCaptor.getValue().getData().getUserId());
        assertEquals(eventCaptor.getValue().getId().toString(), payloadCaptor.getValue().getId(),
                "webhook.test payload.id must match the webhook_events row id");

        ArgumentCaptor<WebhookDeliveryEntity> deliveryCaptor = ArgumentCaptor.forClass(WebhookDeliveryEntity.class);
        verify(webhookDeliveryRepository).save(deliveryCaptor.capture());
        assertEquals(endpoint.getId(), deliveryCaptor.getValue().getEndpointId());
        assertEquals(PAYLOAD_JSON, deliveryCaptor.getValue().getPayload());
    }

    private static WebhookEventCreatedEvent event(
            String eventId,
            String eventKey,
            String eventType,
            String aggregateType,
            String aggregateId,
            String userId,
            String externalReference,
            String payload
    ) {
        return new WebhookEventCreatedEvent(
                eventId, eventKey, eventType, aggregateType, aggregateId, userId, externalReference, payload);
    }

    private WebhookEndpointEntity enabledEndpoint(String eventType) {
        WebhookEndpointEntity endpoint = WebhookEndpointEntity.builder()
                .id(java.util.UUID.randomUUID())
                .name("Test Endpoint")
                .url("https://example.com/webhook")
                .signingSecret("secret")
                .enabled(true)
                .build();
        WebhookEndpointSubscriptionEntity sub = WebhookEndpointSubscriptionEntity.builder()
                .endpoint(endpoint)
                .eventType(eventType)
                .build();
        endpoint.setSubscriptions(Set.of(sub));
        return endpoint;
    }

}
