package com.aratiri.webhooks.application;

import com.aratiri.infrastructure.persistence.jpa.entity.WebhookDeliveryEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.WebhookDeliveryStatus;
import com.aratiri.infrastructure.persistence.jpa.entity.WebhookEndpointEntity;
import com.aratiri.infrastructure.persistence.jpa.repository.WebhookDeliveryRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.WebhookEndpointRepository;
import com.aratiri.shared.exception.AratiriException;
import com.aratiri.webhooks.application.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookAdminServiceTest {

    @Mock
    private WebhookEndpointRepository webhookEndpointRepository;

    @Mock
    private WebhookDeliveryRepository webhookDeliveryRepository;

    @Mock
    private WebhookEventService webhookEventService;

    private WebhookAdminService webhookAdminService;

    @BeforeEach
    void setUp() {
        webhookAdminService = new WebhookAdminService(webhookEndpointRepository, webhookDeliveryRepository, webhookEventService);
    }

    @Test
    void createEndpoint_persistsSubscriptionsAndReturnsSecret() {
        CreateWebhookEndpointRequestDTO request = new CreateWebhookEndpointRequestDTO();
        request.setName("Test Endpoint");
        request.setUrl("https://example.com/webhook");
        request.setEventTypes(Set.of("payment.succeeded"));
        request.setEnabled(true);

        when(webhookEndpointRepository.save(any())).thenAnswer(inv -> {
            WebhookEndpointEntity e = inv.getArgument(0);
            e.setId(java.util.UUID.randomUUID());
            return e;
        });

        WebhookSecretResponseDTO response = webhookAdminService.createEndpoint(request);

        assertNotNull(response.getId());
        assertNotNull(response.getSigningSecret());
        assertTrue(response.getSigningSecret().length() > 20);
        ArgumentCaptor<WebhookEndpointEntity> captor = ArgumentCaptor.forClass(WebhookEndpointEntity.class);
        verify(webhookEndpointRepository).save(captor.capture());
        assertEquals(1, captor.getValue().getSubscriptions().size());
    }

    @Test
    void rotateSecret_changesSecret() {
        UUID id = UUID.randomUUID();
        WebhookEndpointEntity endpoint = WebhookEndpointEntity.builder()
                .id(id)
                .name("Test")
                .url("https://example.com")
                .signingSecret("old-secret")
                .enabled(true)
                .build();
        when(webhookEndpointRepository.findById(id)).thenReturn(Optional.of(endpoint));
        when(webhookEndpointRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WebhookSecretResponseDTO response = webhookAdminService.rotateSecret(id);

        assertNotEquals("old-secret", response.getSigningSecret());
        assertEquals(id, response.getId());
    }

    @Test
    void sendTestEvent_triggersWebhookTestEvent() {
        UUID id = UUID.randomUUID();
        WebhookEndpointEntity endpoint = WebhookEndpointEntity.builder()
                .id(id)
                .name("Test")
                .url("https://example.com")
                .signingSecret("secret")
                .enabled(true)
                .build();
        when(webhookEndpointRepository.findById(id)).thenReturn(Optional.of(endpoint));

        webhookAdminService.sendTestEvent(id);

        verify(webhookEventService).createWebhookTestEvent(endpoint);
    }

    @Test
    void retryDelivery_succeedsForFailedDelivery() {
        UUID id = UUID.randomUUID();
        WebhookDeliveryEntity delivery = WebhookDeliveryEntity.builder()
                .id(id)
                .status(WebhookDeliveryStatus.FAILED)
                .attemptCount(5)
                .nextAttemptAt(java.time.Instant.now().plusSeconds(3600))
                .lastError("error")
                .build();
        when(webhookDeliveryRepository.findById(id)).thenReturn(Optional.of(delivery));
        when(webhookDeliveryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        webhookAdminService.retryDelivery(id);

        assertEquals(WebhookDeliveryStatus.PENDING, delivery.getStatus());
        assertNull(delivery.getLastError());
    }

    @Test
    void retryDelivery_throwsForSucceededDelivery() {
        UUID id = UUID.randomUUID();
        WebhookDeliveryEntity delivery = WebhookDeliveryEntity.builder()
                .id(id)
                .status(WebhookDeliveryStatus.SUCCEEDED)
                .build();
        when(webhookDeliveryRepository.findById(id)).thenReturn(Optional.of(delivery));

        AratiriException ex = assertThrows(AratiriException.class, () -> webhookAdminService.retryDelivery(id));
        assertTrue(ex.getMessage().contains("Cannot retry a succeeded delivery"));
    }

    @Test
    void listDeliveries_usesCorrectRepositoryMethod() {
        UUID endpointId = UUID.randomUUID();
        when(webhookDeliveryRepository.findByEndpointIdAndStatusAndEventTypeOrderByCreatedAtDesc(any(), any(), any(), any()))
                .thenReturn(List.of());

        webhookAdminService.listDeliveries(endpointId, WebhookDeliveryStatus.PENDING, "payment.succeeded", 50);

        verify(webhookDeliveryRepository).findByEndpointIdAndStatusAndEventTypeOrderByCreatedAtDesc(
                endpointId, WebhookDeliveryStatus.PENDING, "payment.succeeded", PageRequest.of(0, 50));
    }
}
