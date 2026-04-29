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
import org.springframework.data.domain.PageRequest;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookDeliveryServiceTest {

    @Mock
    private WebhookDeliveryRepository webhookDeliveryRepository;

    @Mock
    private WebhookEndpointRepository webhookEndpointRepository;

    @Mock
    private WebhookDeliverySender webhookDeliverySender;

    private WebhookDeliveryService webhookDeliveryService;

    @BeforeEach
    void setUp() {
        webhookDeliveryService = new WebhookDeliveryService(
                webhookDeliveryRepository,
                webhookEndpointRepository,
                webhookDeliverySender
        );
    }

    @Test
    void generateSignature_producesExpectedFormat() {
        String signature = JavaHttpWebhookDeliverySender.generateSignature(
                "mysecret",
                "1714042800",
                "evt-123",
                "{\"type\":\"test\"}"
        );

        assertTrue(signature.startsWith("v1="));
        assertEquals(67, signature.length());
    }

    @Test
    void generateSignature_differentPayloadsProduceDifferentSignatures() {
        String sig1 = JavaHttpWebhookDeliverySender.generateSignature("mysecret", "1", "a", "payload1");
        String sig2 = JavaHttpWebhookDeliverySender.generateSignature("mysecret", "1", "a", "payload2");
        assertNotEquals(sig1, sig2);
    }

    @Test
    void generateSignature_differentSecretsProduceDifferentSignatures() {
        String sig1 = JavaHttpWebhookDeliverySender.generateSignature("secret1", "1", "a", "payload");
        String sig2 = JavaHttpWebhookDeliverySender.generateSignature("secret2", "1", "a", "payload");
        assertNotEquals(sig1, sig2);
    }

    @Test
    void claimRunnableDeliveries_locksOnlyDeliveriesWonByThisWorker() {
        WebhookDeliveryEntity claimed = deliveryWithAttempts(0);
        WebhookDeliveryEntity alreadyClaimed = deliveryWithAttempts(0);
        when(webhookDeliveryRepository.findRunnableDeliveries(any(Instant.class), eq(PageRequest.of(0, 50))))
                .thenReturn(List.of(claimed, alreadyClaimed));
        when(webhookDeliveryRepository.claimDelivery(eq(claimed.getId()), anyString(), any(Instant.class), any(Instant.class)))
                .thenReturn(1);
        when(webhookDeliveryRepository.claimDelivery(eq(alreadyClaimed.getId()), anyString(), any(Instant.class), any(Instant.class)))
                .thenReturn(0);

        List<WebhookDeliveryEntity> deliveries = webhookDeliveryService.claimRunnableDeliveries();

        assertEquals(List.of(claimed), deliveries);
        assertNotNull(claimed.getLockedBy());
        assertTrue(claimed.getLockedBy().startsWith("webhook-worker-"));
        assertNotNull(claimed.getLockedUntil());
        assertNull(alreadyClaimed.getLockedBy());
        assertNull(alreadyClaimed.getLockedUntil());
    }

    @Test
    void attemptDelivery_successRecordsStatusBodyAttemptsLockClearAndEndpointSuccess() throws Exception {
        WebhookDeliveryEntity delivery = deliveryWithAttempts(0);
        delivery.setLockedBy("worker");
        delivery.setLockedUntil(Instant.now().plusSeconds(120));
        WebhookEndpointEntity endpoint = endpoint(delivery.getEndpointId(), true);
        when(webhookEndpointRepository.findById(delivery.getEndpointId())).thenReturn(Optional.of(endpoint));
        when(webhookDeliverySender.send(delivery, endpoint)).thenReturn(new WebhookSendResult(204, "accepted"));

        webhookDeliveryService.attemptDelivery(delivery);

        assertEquals(1, delivery.getAttemptCount());
        assertEquals(WebhookDeliveryStatus.SUCCEEDED, delivery.getStatus());
        assertNotNull(delivery.getDeliveredAt());
        assertNull(delivery.getLastError());
        assertNull(delivery.getLockedBy());
        assertNull(delivery.getLockedUntil());
        assertEquals(204, delivery.getResponseStatus());
        assertEquals("accepted", delivery.getResponseBody());
        assertNotNull(endpoint.getLastSuccessAt());
        verify(webhookDeliveryRepository).save(delivery);
        verify(webhookEndpointRepository).save(endpoint);
    }

    @Test
    void attemptDelivery_non2xxRecordsResponseAndSchedulesRetryWithOneEndpointFailureTimestamp() throws Exception {
        WebhookDeliveryEntity delivery = deliveryWithAttempts(0);
        delivery.setLockedBy("worker");
        delivery.setLockedUntil(Instant.now().plusSeconds(120));
        WebhookEndpointEntity endpoint = endpoint(delivery.getEndpointId(), true);
        when(webhookEndpointRepository.findById(delivery.getEndpointId())).thenReturn(Optional.of(endpoint));
        when(webhookDeliverySender.send(delivery, endpoint)).thenReturn(new WebhookSendResult(500, "server error"));

        webhookDeliveryService.attemptDelivery(delivery);

        assertEquals(1, delivery.getAttemptCount());
        assertEquals(WebhookDeliveryStatus.PENDING, delivery.getStatus());
        assertEquals(500, delivery.getResponseStatus());
        assertEquals("server error", delivery.getResponseBody());
        assertEquals("HTTP 500", delivery.getLastError());
        assertNull(delivery.getLockedBy());
        assertNull(delivery.getLockedUntil());
        assertNotNull(delivery.getNextAttemptAt());
        assertNotNull(endpoint.getLastFailureAt());
        verify(webhookEndpointRepository, times(1)).save(endpoint);
        verify(webhookDeliveryRepository, times(1)).save(delivery);
    }

    @Test
    void attemptDelivery_ioFailureRecordsLastErrorAndSchedulesRetry() throws Exception {
        WebhookDeliveryEntity delivery = deliveryWithAttempts(0);
        WebhookEndpointEntity endpoint = endpoint(delivery.getEndpointId(), true);
        when(webhookEndpointRepository.findById(delivery.getEndpointId())).thenReturn(Optional.of(endpoint));
        when(webhookDeliverySender.send(delivery, endpoint)).thenThrow(new IOException("socket closed"));

        webhookDeliveryService.attemptDelivery(delivery);

        assertEquals(1, delivery.getAttemptCount());
        assertEquals(WebhookDeliveryStatus.PENDING, delivery.getStatus());
        assertEquals("IOException: socket closed", delivery.getLastError());
        assertNull(delivery.getLockedBy());
        assertNull(delivery.getLockedUntil());
        verify(webhookDeliveryRepository).save(delivery);
    }

    @Test
    void attemptDelivery_interruptedFailureRestoresInterruptFlagAndSchedulesRetry() throws Exception {
        Thread.interrupted();
        WebhookDeliveryEntity delivery = deliveryWithAttempts(0);
        WebhookEndpointEntity endpoint = endpoint(delivery.getEndpointId(), true);
        when(webhookEndpointRepository.findById(delivery.getEndpointId())).thenReturn(Optional.of(endpoint));
        when(webhookDeliverySender.send(delivery, endpoint)).thenThrow(new InterruptedException("stop"));

        webhookDeliveryService.attemptDelivery(delivery);

        assertEquals(1, delivery.getAttemptCount());
        assertEquals(WebhookDeliveryStatus.PENDING, delivery.getStatus());
        assertEquals("Interrupted: stop", delivery.getLastError());
        assertTrue(Thread.currentThread().isInterrupted());
        Thread.interrupted();
        verify(webhookDeliveryRepository).save(delivery);
    }

    @Test
    void attemptDelivery_missingEndpointSchedulesRetryWithoutCallingSender() {
        WebhookDeliveryEntity delivery = deliveryWithAttempts(0);
        when(webhookEndpointRepository.findById(delivery.getEndpointId())).thenReturn(Optional.empty());

        webhookDeliveryService.attemptDelivery(delivery);

        assertEquals(1, delivery.getAttemptCount());
        assertEquals(WebhookDeliveryStatus.PENDING, delivery.getStatus());
        assertEquals("Endpoint not found", delivery.getLastError());
        verifyNoInteractions(webhookDeliverySender);
        verify(webhookDeliveryRepository).save(delivery);
    }

    @Test
    void attemptDelivery_disabledEndpointSchedulesRetryWithoutCallingSender() {
        WebhookDeliveryEntity delivery = deliveryWithAttempts(0);
        WebhookEndpointEntity endpoint = endpoint(delivery.getEndpointId(), false);
        when(webhookEndpointRepository.findById(delivery.getEndpointId())).thenReturn(Optional.of(endpoint));

        webhookDeliveryService.attemptDelivery(delivery);

        assertEquals(1, delivery.getAttemptCount());
        assertEquals(WebhookDeliveryStatus.PENDING, delivery.getStatus());
        assertEquals("Endpoint disabled", delivery.getLastError());
        assertNotNull(endpoint.getLastFailureAt());
        verifyNoInteractions(webhookDeliverySender);
        verify(webhookDeliveryRepository).save(delivery);
    }

    @Test
    void scheduleRetryOrFail_firstFailureUsesOneMinuteDelay() {
        WebhookDeliveryEntity delivery = deliveryWithAttempts(0);
        WebhookEndpointEntity endpoint = endpoint(delivery.getEndpointId(), true);
        when(webhookEndpointRepository.findById(delivery.getEndpointId())).thenReturn(Optional.of(endpoint));

        webhookDeliveryService.scheduleRetryOrFail(delivery);

        assertEquals(1, delivery.getAttemptCount());
        assertEquals(WebhookDeliveryStatus.PENDING, delivery.getStatus());
        assertTrue(delivery.getNextAttemptAt().isAfter(Instant.now()));
        assertTrue(delivery.getNextAttemptAt().isBefore(Instant.now().plusSeconds(120)));
        assertNotNull(endpoint.getLastFailureAt());
        verify(webhookDeliveryRepository).save(delivery);
    }

    @Test
    void scheduleRetryOrFail_marksFailedAfterMaxAttempts() {
        WebhookDeliveryEntity delivery = deliveryWithAttempts(11);
        WebhookEndpointEntity endpoint = endpoint(delivery.getEndpointId(), true);
        when(webhookEndpointRepository.findById(delivery.getEndpointId())).thenReturn(Optional.of(endpoint));

        webhookDeliveryService.scheduleRetryOrFail(delivery);

        assertEquals(12, delivery.getAttemptCount());
        assertEquals(WebhookDeliveryStatus.FAILED, delivery.getStatus());
        verify(webhookDeliveryRepository).save(delivery);
    }

    @Test
    void resetForManualRetry_resetsStatusToPendingAndClearsLockAndError() {
        WebhookDeliveryEntity delivery = deliveryWithAttempts(5);
        delivery.setStatus(WebhookDeliveryStatus.FAILED);
        delivery.setLastError("error");
        delivery.setLockedBy("worker");
        delivery.setLockedUntil(Instant.now().plusSeconds(120));

        webhookDeliveryService.resetForManualRetry(delivery);

        assertEquals(WebhookDeliveryStatus.PENDING, delivery.getStatus());
        assertNull(delivery.getLastError());
        assertNull(delivery.getLockedBy());
        assertNull(delivery.getLockedUntil());
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

    private WebhookEndpointEntity endpoint(UUID endpointId, boolean enabled) {
        return WebhookEndpointEntity.builder()
                .id(endpointId)
                .name("Test")
                .url("https://example.com")
                .signingSecret("secret")
                .enabled(enabled)
                .build();
    }
}
