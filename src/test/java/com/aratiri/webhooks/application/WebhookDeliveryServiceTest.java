package com.aratiri.webhooks.application;

import com.aratiri.infrastructure.persistence.jpa.entity.WebhookDeliveryEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.WebhookDeliveryStatus;
import com.aratiri.infrastructure.persistence.jpa.entity.WebhookEndpointEntity;
import com.aratiri.infrastructure.persistence.jpa.repository.WebhookDeliveryRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.WebhookEndpointRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookDeliveryServiceTest {

    @Mock
    private WebhookDeliveryRepository webhookDeliveryRepository;

    @Mock
    private WebhookEndpointRepository webhookEndpointRepository;

    private WebhookDeliveryService webhookDeliveryService;

    @BeforeEach
    void setUp() {
        webhookDeliveryService = new WebhookDeliveryService(webhookDeliveryRepository, webhookEndpointRepository);
    }

    @Test
    void generateSignature_producesExpectedFormat() {
        String secret = "mysecret";
        String timestamp = "1714042800";
        String eventId = "evt-123";
        String payload = "{\"type\":\"test\"}";

        String signature = WebhookDeliveryService.generateSignature(secret, timestamp, eventId, payload);

        assertTrue(signature.startsWith("v1="));
        assertEquals(67, signature.length()); // "v1=" + 64 hex chars
    }

    @Test
    void generateSignature_differentPayloadsProduceDifferentSignatures() {
        String secret = "mysecret";
        String sig1 = WebhookDeliveryService.generateSignature(secret, "1", "a", "payload1");
        String sig2 = WebhookDeliveryService.generateSignature(secret, "1", "a", "payload2");
        assertNotEquals(sig1, sig2);
    }

    @Test
    void generateSignature_differentSecretsProduceDifferentSignatures() {
        String sig1 = WebhookDeliveryService.generateSignature("secret1", "1", "a", "payload");
        String sig2 = WebhookDeliveryService.generateSignature("secret2", "1", "a", "payload");
        assertNotEquals(sig1, sig2);
    }

    @Test
    void scheduleRetryOrFail_firstFailureUsesOneMinuteDelay() {
        WebhookDeliveryEntity delivery = deliveryWithAttempts(0);
        UUID endpointId = UUID.randomUUID();
        delivery.setEndpointId(endpointId);

        WebhookEndpointEntity endpoint = WebhookEndpointEntity.builder()
                .id(endpointId)
                .name("Test")
                .url("https://example.com")
                .signingSecret("secret")
                .enabled(true)
                .build();
        when(webhookEndpointRepository.findById(endpointId)).thenReturn(Optional.of(endpoint));

        webhookDeliveryService.scheduleRetryOrFail(delivery);

        assertEquals(1, delivery.getAttemptCount());
        assertEquals(WebhookDeliveryStatus.PENDING, delivery.getStatus());
        assertTrue(delivery.getNextAttemptAt().isAfter(Instant.now()));
        assertTrue(delivery.getNextAttemptAt().isBefore(Instant.now().plusSeconds(120)));
        verify(webhookDeliveryRepository).save(delivery);
    }

    @Test
    void scheduleRetryOrFail_schedulesNextRetryWithinMaxAttempts() {
        WebhookDeliveryEntity delivery = deliveryWithAttempts(1);
        UUID endpointId = UUID.randomUUID();
        delivery.setEndpointId(endpointId);

        WebhookEndpointEntity endpoint = WebhookEndpointEntity.builder()
                .id(endpointId)
                .name("Test")
                .url("https://example.com")
                .signingSecret("secret")
                .enabled(true)
                .build();
        when(webhookEndpointRepository.findById(endpointId)).thenReturn(Optional.of(endpoint));

        webhookDeliveryService.scheduleRetryOrFail(delivery);

        assertEquals(2, delivery.getAttemptCount());
        assertEquals(WebhookDeliveryStatus.PENDING, delivery.getStatus());
        assertTrue(delivery.getNextAttemptAt().isAfter(Instant.now()));
        verify(webhookDeliveryRepository).save(delivery);
    }

    @Test
    void scheduleRetryOrFail_marksFailedAfterMaxAttempts() {
        WebhookDeliveryEntity delivery = deliveryWithAttempts(11);
        UUID endpointId = UUID.randomUUID();
        delivery.setEndpointId(endpointId);

        WebhookEndpointEntity endpoint = WebhookEndpointEntity.builder()
                .id(endpointId)
                .name("Test")
                .url("https://example.com")
                .signingSecret("secret")
                .enabled(true)
                .build();
        when(webhookEndpointRepository.findById(endpointId)).thenReturn(Optional.of(endpoint));

        webhookDeliveryService.scheduleRetryOrFail(delivery);

        assertEquals(12, delivery.getAttemptCount());
        assertEquals(WebhookDeliveryStatus.FAILED, delivery.getStatus());
        verify(webhookDeliveryRepository).save(delivery);
    }

    @Test
    void resetForManualRetry_resetsStatusToPending() {
        WebhookDeliveryEntity delivery = deliveryWithAttempts(5);
        delivery.setStatus(WebhookDeliveryStatus.FAILED);
        delivery.setLastError("error");

        webhookDeliveryService.resetForManualRetry(delivery);

        assertEquals(WebhookDeliveryStatus.PENDING, delivery.getStatus());
        assertNull(delivery.getLastError());
        assertTrue(delivery.getNextAttemptAt().isBefore(Instant.now().plusSeconds(1)));
        verify(webhookDeliveryRepository).save(delivery);
    }

    private WebhookDeliveryEntity deliveryWithAttempts(int attempts) {
        return WebhookDeliveryEntity.builder()
                .id(UUID.randomUUID())
                .eventId(UUID.randomUUID())
                .endpointId(UUID.randomUUID())
                .eventType("payment.succeeded")
                .payload("{}")
                .status(WebhookDeliveryStatus.PENDING)
                .attemptCount(attempts)
                .nextAttemptAt(Instant.now())
                .build();
    }
}
