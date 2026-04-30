package com.aratiri.webhooks.application;

import com.aratiri.infrastructure.persistence.jpa.entity.WebhookDeliveryEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.WebhookDeliveryStatus;
import com.aratiri.infrastructure.persistence.jpa.entity.WebhookEndpointEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.WebhookEndpointSubscriptionEntity;
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

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookAdminServiceTest {

  @Mock
  private WebhookEndpointRepository webhookEndpointRepository;

  @Mock
  private WebhookDeliveryRepository webhookDeliveryRepository;

  @Mock
  private WebhookEventService webhookEventService;

  @Mock
  private WebhookDeliveryLifecycle webhookDeliveryLifecycle;

  private WebhookAdminService webhookAdminService;

  @BeforeEach
  void setUp() {
    webhookAdminService = new WebhookAdminService(
        webhookEndpointRepository,
        webhookDeliveryRepository,
        webhookEventService,
        webhookDeliveryLifecycle
    );
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
      e.setId(UUID.randomUUID());
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
  void createEndpoint_multipleEventTypes() {
    CreateWebhookEndpointRequestDTO request = new CreateWebhookEndpointRequestDTO();
    request.setName("Multi Event");
    request.setUrl("https://example.com/webhook");
    request.setEventTypes(Set.of("payment.succeeded", "invoice.created", "payment.failed"));
    request.setEnabled(false);

    when(webhookEndpointRepository.save(any())).thenAnswer(inv -> {
      WebhookEndpointEntity e = inv.getArgument(0);
      e.setId(UUID.randomUUID());
      return e;
    });

    WebhookSecretResponseDTO response = webhookAdminService.createEndpoint(request);

    assertNotNull(response.getId());
    assertNotNull(response.getSigningSecret());
    ArgumentCaptor<WebhookEndpointEntity> captor = ArgumentCaptor.forClass(WebhookEndpointEntity.class);
    verify(webhookEndpointRepository).save(captor.capture());
    WebhookEndpointEntity saved = captor.getValue();
    assertEquals(3, saved.getSubscriptions().size());
    assertEquals("Multi Event", saved.getName());
    assertFalse(saved.getEnabled());
  }

  @Test
  void createEndpoint_generatesUniqueSecretEachTime() {
    CreateWebhookEndpointRequestDTO request = new CreateWebhookEndpointRequestDTO();
    request.setName("Secret Test");
    request.setUrl("https://example.com/webhook");
    request.setEventTypes(Set.of("payment.succeeded"));
    request.setEnabled(true);

    when(webhookEndpointRepository.save(any())).thenAnswer(inv -> {
      WebhookEndpointEntity e = inv.getArgument(0);
      e.setId(UUID.randomUUID());
      return e;
    });

    WebhookSecretResponseDTO response1 = webhookAdminService.createEndpoint(request);
    WebhookSecretResponseDTO response2 = webhookAdminService.createEndpoint(request);

    assertNotEquals(response1.getSigningSecret(), response2.getSigningSecret());
  }

  @Test
  void listEndpoints_returnsAllEndpoints() {
    WebhookEndpointEntity endpoint = endpointEntity("test", "https://example.com", Set.of("payment.succeeded"));
    when(webhookEndpointRepository.findAll()).thenReturn(List.of(endpoint));

    List<WebhookEndpointResponseDTO> result = webhookAdminService.listEndpoints();

    assertEquals(1, result.size());
    WebhookEndpointResponseDTO dto = result.get(0);
    assertEquals(endpoint.getId(), dto.getId());
    assertEquals("test", dto.getName());
    assertEquals("https://example.com", dto.getUrl());
    assertEquals(Set.of("payment.succeeded"), dto.getEventTypes());
    assertTrue(dto.getEnabled());
  }

  @Test
  void listEndpoints_returnsEmptyWhenNone() {
    when(webhookEndpointRepository.findAll()).thenReturn(List.of());

    List<WebhookEndpointResponseDTO> result = webhookAdminService.listEndpoints();

    assertTrue(result.isEmpty());
  }

  @Test
  void listEndpoints_returnsMultipleEndpoints() {
    WebhookEndpointEntity e1 = endpointEntity("ep1", "https://a.com", Set.of("payment.succeeded"));
    WebhookEndpointEntity e2 = endpointEntity("ep2", "https://b.com", Set.of("invoice.created"));
    when(webhookEndpointRepository.findAll()).thenReturn(List.of(e1, e2));

    List<WebhookEndpointResponseDTO> result = webhookAdminService.listEndpoints();

    assertEquals(2, result.size());
    assertEquals("ep1", result.get(0).getName());
    assertEquals("ep2", result.get(1).getName());
  }

  @Test
  void getEndpoint_returnsEndpoint() {
    UUID id = UUID.randomUUID();
    WebhookEndpointEntity endpoint = endpointEntity("test", "https://example.com", Set.of("payment.succeeded"));
    endpoint.setId(id);
    when(webhookEndpointRepository.findById(id)).thenReturn(Optional.of(endpoint));

    WebhookEndpointResponseDTO result = webhookAdminService.getEndpoint(id);

    assertEquals(id, result.getId());
    assertEquals("test", result.getName());
    assertEquals("https://example.com", result.getUrl());
    assertEquals(Set.of("payment.succeeded"), result.getEventTypes());
  }

  @Test
  void getEndpoint_throwsWhenNotFound() {
    UUID id = UUID.randomUUID();
    when(webhookEndpointRepository.findById(id)).thenReturn(Optional.empty());

    AratiriException ex = assertThrows(AratiriException.class, () -> webhookAdminService.getEndpoint(id));
    assertTrue(ex.getMessage().contains("Webhook endpoint not found"));
  }

  @Test
  void updateEndpoint_updatesFieldsAndReplacesSubscriptions() {
    UUID id = UUID.randomUUID();
    WebhookEndpointEntity existing = endpointEntity("Old Name", "https://old.example.com", Set.of("old.event"));
    existing.setId(id);
    when(webhookEndpointRepository.findById(id)).thenReturn(Optional.of(existing));
    when(webhookEndpointRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    UpdateWebhookEndpointRequestDTO request = new UpdateWebhookEndpointRequestDTO();
    request.setName("New Name");
    request.setUrl("https://new.example.com");
    request.setEventTypes(Set.of("payment.succeeded", "invoice.created"));
    request.setEnabled(false);

    WebhookEndpointResponseDTO result = webhookAdminService.updateEndpoint(id, request);

    assertEquals("New Name", result.getName());
    assertEquals("https://new.example.com", result.getUrl());
    assertEquals(Set.of("payment.succeeded", "invoice.created"), result.getEventTypes());
    assertFalse(result.getEnabled());
    ArgumentCaptor<WebhookEndpointEntity> captor = ArgumentCaptor.forClass(WebhookEndpointEntity.class);
    verify(webhookEndpointRepository).save(captor.capture());
    assertEquals(2, captor.getValue().getSubscriptions().size());
  }

  @Test
  void updateEndpoint_throwsWhenEndpointNotFound() {
    UUID id = UUID.randomUUID();
    when(webhookEndpointRepository.findById(id)).thenReturn(Optional.empty());

    UpdateWebhookEndpointRequestDTO request = new UpdateWebhookEndpointRequestDTO();
    request.setName("New");
    request.setUrl("https://new.example.com");
    request.setEventTypes(Set.of("payment.succeeded"));
    request.setEnabled(true);

    AratiriException ex = assertThrows(AratiriException.class,
        () -> webhookAdminService.updateEndpoint(id, request));
    assertTrue(ex.getMessage().contains("Webhook endpoint not found"));
  }

  @Test
  void updateEndpoint_clearsPreviousSubscriptions() {
    UUID id = UUID.randomUUID();
    WebhookEndpointEntity existing = endpointEntity("Old", "https://old.example.com",
        Set.of("old.event1", "old.event2", "old.event3"));
    existing.setId(id);
    when(webhookEndpointRepository.findById(id)).thenReturn(Optional.of(existing));
    when(webhookEndpointRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    UpdateWebhookEndpointRequestDTO request = new UpdateWebhookEndpointRequestDTO();
    request.setName("Old");
    request.setUrl("https://old.example.com");
    request.setEventTypes(Set.of("new.event"));
    request.setEnabled(true);

    WebhookEndpointResponseDTO result = webhookAdminService.updateEndpoint(id, request);

    assertEquals(Set.of("new.event"), result.getEventTypes());
    ArgumentCaptor<WebhookEndpointEntity> captor = ArgumentCaptor.forClass(WebhookEndpointEntity.class);
    verify(webhookEndpointRepository).save(captor.capture());
    assertEquals(1, captor.getValue().getSubscriptions().size());
    assertEquals("new.event", captor.getValue().getSubscriptions().iterator().next().getEventType());
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
  void rotateSecret_throwsWhenEndpointNotFound() {
    UUID id = UUID.randomUUID();
    when(webhookEndpointRepository.findById(id)).thenReturn(Optional.empty());

    AratiriException ex = assertThrows(AratiriException.class, () -> webhookAdminService.rotateSecret(id));
    assertTrue(ex.getMessage().contains("Webhook endpoint not found"));
  }

  @Test
  void rotateSecret_persistsUpdatedSecret() {
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

    webhookAdminService.rotateSecret(id);

    ArgumentCaptor<WebhookEndpointEntity> captor = ArgumentCaptor.forClass(WebhookEndpointEntity.class);
    verify(webhookEndpointRepository).save(captor.capture());
    assertNotEquals("old-secret", captor.getValue().getSigningSecret());
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

    ArgumentCaptor<WebhookTestEventFacts> captor = ArgumentCaptor.forClass(WebhookTestEventFacts.class);
    verify(webhookEventService).createWebhookTestEvent(captor.capture());
    assertEquals(id, captor.getValue().endpointId());
  }

  @Test
  void sendTestEvent_throwsWhenEndpointNotFound() {
    UUID id = UUID.randomUUID();
    when(webhookEndpointRepository.findById(id)).thenReturn(Optional.empty());

    AratiriException ex = assertThrows(AratiriException.class,
        () -> webhookAdminService.sendTestEvent(id));
    assertTrue(ex.getMessage().contains("Webhook endpoint not found"));
  }

  @Test
  void retryDelivery_reusesLifecycleManualResetForFailedDelivery() {
    UUID id = UUID.randomUUID();
    WebhookDeliveryEntity delivery = WebhookDeliveryEntity.builder()
        .id(id)
        .status(WebhookDeliveryStatus.FAILED)
        .attemptCount(5)
        .nextAttemptAt(Instant.now().plusSeconds(3600))
        .lastError("error")
        .build();
    when(webhookDeliveryRepository.findById(id)).thenReturn(Optional.of(delivery));

    webhookAdminService.retryDelivery(id);

    verify(webhookDeliveryLifecycle).resetForManualRetry(delivery);
    verify(webhookDeliveryRepository, never()).save(any());
  }

  @Test
  void retryDelivery_throwsForSucceededDelivery() {
    UUID id = UUID.randomUUID();
    WebhookDeliveryEntity delivery = WebhookDeliveryEntity.builder()
        .id(id)
        .status(WebhookDeliveryStatus.SUCCEEDED)
        .build();
    when(webhookDeliveryRepository.findById(id)).thenReturn(Optional.of(delivery));

    AratiriException ex = assertThrows(AratiriException.class,
        () -> webhookAdminService.retryDelivery(id));
    assertTrue(ex.getMessage().contains("Cannot retry a succeeded delivery"));
  }

  @Test
  void retryDelivery_throwsWhenDeliveryNotFound() {
    UUID id = UUID.randomUUID();
    when(webhookDeliveryRepository.findById(id)).thenReturn(Optional.empty());

    AratiriException ex = assertThrows(AratiriException.class,
        () -> webhookAdminService.retryDelivery(id));
    assertTrue(ex.getMessage().contains("Webhook delivery not found"));
  }

  @Test
  void retryDelivery_allowsRetryForPendingDelivery() {
    UUID id = UUID.randomUUID();
    WebhookDeliveryEntity delivery = WebhookDeliveryEntity.builder()
        .id(id)
        .status(WebhookDeliveryStatus.PENDING)
        .attemptCount(2)
        .build();
    when(webhookDeliveryRepository.findById(id)).thenReturn(Optional.of(delivery));

    webhookAdminService.retryDelivery(id);

    verify(webhookDeliveryLifecycle).resetForManualRetry(delivery);
  }

  @Test
  void getDelivery_returnsDeliveryWithAllFields() {
    UUID id = UUID.randomUUID();
    UUID eventId = UUID.randomUUID();
    UUID endpointId = UUID.randomUUID();
    Instant now = Instant.now();
    WebhookDeliveryEntity delivery = WebhookDeliveryEntity.builder()
        .id(id)
        .eventId(eventId)
        .endpointId(endpointId)
        .eventType("payment.succeeded")
        .payload("{}")
        .status(WebhookDeliveryStatus.SUCCEEDED)
        .attemptCount(3)
        .nextAttemptAt(now.plusSeconds(300))
        .responseStatus(200)
        .responseBody("ok-body")
        .lastError("ok-body")
        .createdAt(now)
        .updatedAt(now)
        .deliveredAt(now)
        .build();
    when(webhookDeliveryRepository.findByIdWithEvent(id)).thenReturn(Optional.of(delivery));

    WebhookDeliveryResponseDTO result = webhookAdminService.getDelivery(id);

    assertEquals(id, result.getId());
    assertEquals(eventId, result.getEventId());
    assertEquals(endpointId, result.getEndpointId());
    assertEquals("SUCCEEDED", result.getStatus());
    assertEquals("payment.succeeded", result.getEventType());
    assertEquals(3, result.getAttemptCount());
    assertEquals(200, result.getResponseStatus());
    assertEquals("ok-body", result.getLastError());
    assertNotNull(result.getCreatedAt());
    assertNotNull(result.getUpdatedAt());
    assertNotNull(result.getDeliveredAt());
    assertNotNull(result.getNextAttemptAt());
  }

  @Test
  void getDelivery_throwsWhenNotFound() {
    UUID id = UUID.randomUUID();
    when(webhookDeliveryRepository.findByIdWithEvent(id)).thenReturn(Optional.empty());

    AratiriException ex = assertThrows(AratiriException.class,
        () -> webhookAdminService.getDelivery(id));
    assertTrue(ex.getMessage().contains("Webhook delivery not found"));
  }

  @Test
  void listDeliveries_usesCorrectRepositoryMethod_allFilters() {
    UUID endpointId = UUID.randomUUID();
    when(webhookDeliveryRepository.findByEndpointIdAndStatusAndEventTypeOrderByCreatedAtDesc(
        any(), any(), any(), any())).thenReturn(List.of());

    webhookAdminService.listDeliveries(endpointId, WebhookDeliveryStatus.PENDING,
        "payment.succeeded", 50);

    verify(webhookDeliveryRepository).findByEndpointIdAndStatusAndEventTypeOrderByCreatedAtDesc(
        endpointId, WebhookDeliveryStatus.PENDING, "payment.succeeded", PageRequest.of(0, 50));
  }

  @Test
  void listDeliveries_endpointIdOnly() {
    UUID endpointId = UUID.randomUUID();
    when(webhookDeliveryRepository.findByEndpointId(any(), any())).thenReturn(List.of());

    webhookAdminService.listDeliveries(endpointId, null, null, 10);

    verify(webhookDeliveryRepository).findByEndpointId(endpointId, PageRequest.of(0, 10));
  }

  @Test
  void listDeliveries_statusOnly() {
    when(webhookDeliveryRepository.findByStatusOrderByCreatedAtDesc(any(), any()))
        .thenReturn(List.of());

    webhookAdminService.listDeliveries(null, WebhookDeliveryStatus.FAILED, null, 20);

    verify(webhookDeliveryRepository).findByStatusOrderByCreatedAtDesc(
        WebhookDeliveryStatus.FAILED, PageRequest.of(0, 20));
  }

  @Test
  void listDeliveries_eventTypeOnly() {
    when(webhookDeliveryRepository.findByEventTypeOrderByCreatedAtDesc(any(), any()))
        .thenReturn(List.of());

    webhookAdminService.listDeliveries(null, null, "invoice.created", 30);

    verify(webhookDeliveryRepository).findByEventTypeOrderByCreatedAtDesc(
        "invoice.created", PageRequest.of(0, 30));
  }

  @Test
  void listDeliveries_noFilters() {
    when(webhookDeliveryRepository.findAll(any(PageRequest.class)))
        .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));

    webhookAdminService.listDeliveries(null, null, null, 10);

    verify(webhookDeliveryRepository).findAll(PageRequest.of(0, 10));
  }

  @Test
  void listDeliveries_endpointIdAndStatus() {
    UUID endpointId = UUID.randomUUID();
    when(webhookDeliveryRepository.findByEndpointIdAndStatusOrderByCreatedAtDesc(any(), any(), any()))
        .thenReturn(List.of());

    webhookAdminService.listDeliveries(endpointId, WebhookDeliveryStatus.PENDING, null, 40);

    verify(webhookDeliveryRepository).findByEndpointIdAndStatusOrderByCreatedAtDesc(
        endpointId, WebhookDeliveryStatus.PENDING, PageRequest.of(0, 40));
  }

  @Test
  void listDeliveries_endpointIdAndEventType() {
    UUID endpointId = UUID.randomUUID();
    when(webhookDeliveryRepository.findByEndpointIdAndEventTypeOrderByCreatedAtDesc(any(), any(), any()))
        .thenReturn(List.of());

    webhookAdminService.listDeliveries(endpointId, null, "payment.succeeded", 15);

    verify(webhookDeliveryRepository).findByEndpointIdAndEventTypeOrderByCreatedAtDesc(
        endpointId, "payment.succeeded", PageRequest.of(0, 15));
  }

  @Test
  void listDeliveries_statusAndEventType() {
    when(webhookDeliveryRepository.findByStatusAndEventTypeOrderByCreatedAtDesc(any(), any(), any()))
        .thenReturn(List.of());

    webhookAdminService.listDeliveries(null, WebhookDeliveryStatus.FAILED, "payment.failed", 25);

    verify(webhookDeliveryRepository).findByStatusAndEventTypeOrderByCreatedAtDesc(
        WebhookDeliveryStatus.FAILED, "payment.failed", PageRequest.of(0, 25));
  }

  @Test
  void listDeliveries_respectsMaxPageSize() {
    when(webhookDeliveryRepository.findByEndpointId(any(), any())).thenReturn(List.of());

    webhookAdminService.listDeliveries(UUID.randomUUID(), null, null, 1000);

    verify(webhookDeliveryRepository).findByEndpointId(any(), eq(PageRequest.of(0, 500)));
  }

  @Test
  void listDeliveries_mapsDeliveryResponseCorrectly() {
    UUID endpointId = UUID.randomUUID();
    UUID id = UUID.randomUUID();
    UUID eventId = UUID.randomUUID();
    Instant now = Instant.now();
    WebhookDeliveryEntity delivery = WebhookDeliveryEntity.builder()
        .id(id)
        .eventId(eventId)
        .endpointId(endpointId)
        .eventType("payment.succeeded")
        .payload("{}")
        .status(WebhookDeliveryStatus.SUCCEEDED)
        .attemptCount(3)
        .nextAttemptAt(now.plusSeconds(300))
        .responseStatus(200)
        .responseBody("response-body")
        .lastError("response-body")
        .createdAt(now)
        .updatedAt(now)
        .deliveredAt(now)
        .build();
    when(webhookDeliveryRepository.findByEndpointId(any(), any())).thenReturn(List.of(delivery));

    List<WebhookDeliveryResponseDTO> result =
        webhookAdminService.listDeliveries(endpointId, null, null, 10);

    assertEquals(1, result.size());
    WebhookDeliveryResponseDTO dto = result.get(0);
    assertEquals(id, dto.getId());
    assertEquals(eventId, dto.getEventId());
    assertEquals(endpointId, dto.getEndpointId());
    assertEquals("SUCCEEDED", dto.getStatus());
    assertEquals("payment.succeeded", dto.getEventType());
    assertEquals(3, dto.getAttemptCount());
    assertEquals(200, dto.getResponseStatus());
    assertEquals("response-body", dto.getLastError());
  }

  private WebhookEndpointEntity endpointEntity(String name, String url, Set<String> eventTypes) {
    WebhookEndpointEntity endpoint = WebhookEndpointEntity.builder()
        .id(UUID.randomUUID())
        .name(name)
        .url(url)
        .signingSecret("test-secret")
        .enabled(true)
        .createdAt(Instant.now())
        .updatedAt(Instant.now())
        .build();
    Set<WebhookEndpointSubscriptionEntity> subs = new HashSet<>();
    for (String eventType : eventTypes) {
      subs.add(WebhookEndpointSubscriptionEntity.builder()
          .endpoint(endpoint)
          .eventType(eventType)
          .build());
    }
    endpoint.setSubscriptions(subs);
    return endpoint;
  }
}
