package com.aratiri.webhooks.application;

import com.aratiri.infrastructure.persistence.jpa.entity.WebhookDeliveryEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.WebhookDeliveryStatus;
import com.aratiri.infrastructure.persistence.jpa.entity.WebhookEndpointEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.WebhookEndpointSubscriptionEntity;
import com.aratiri.infrastructure.persistence.jpa.repository.WebhookDeliveryRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.WebhookEndpointRepository;
import com.aratiri.shared.exception.AratiriException;
import com.aratiri.webhooks.application.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookAdminService {

    private static final int SECRET_LENGTH_BYTES = 32;
    private static final int MAX_DELIVERY_PAGE_SIZE = 500;

    private final WebhookEndpointRepository webhookEndpointRepository;
    private final WebhookDeliveryRepository webhookDeliveryRepository;
    private final WebhookEventService webhookEventService;
    private final WebhookDeliveryLifecycle webhookDeliveryLifecycle;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public WebhookSecretResponseDTO createEndpoint(CreateWebhookEndpointRequestDTO request) {
        WebhookEndpointEntity endpoint = WebhookEndpointEntity.builder()
                .name(request.getName())
                .url(request.getUrl())
                .signingSecret(generateSecret())
                .enabled(request.getEnabled())
                .build();

        for (String eventType : request.getEventTypes()) {
            WebhookEndpointSubscriptionEntity sub = WebhookEndpointSubscriptionEntity.builder()
                    .endpoint(endpoint)
                    .eventType(eventType)
                    .build();
            endpoint.getSubscriptions().add(sub);
        }

        WebhookEndpointEntity saved = webhookEndpointRepository.save(endpoint);
        return WebhookSecretResponseDTO.builder()
                .id(saved.getId())
                .signingSecret(saved.getSigningSecret())
                .build();
    }

    @Transactional(readOnly = true)
    public List<WebhookEndpointResponseDTO> listEndpoints() {
        return webhookEndpointRepository.findAll().stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public WebhookEndpointResponseDTO getEndpoint(UUID id) {
        WebhookEndpointEntity endpoint = findEndpointOrThrow(id);
        return mapToResponse(endpoint);
    }

    @Transactional
    public WebhookEndpointResponseDTO updateEndpoint(UUID id, UpdateWebhookEndpointRequestDTO request) {
        WebhookEndpointEntity endpoint = findEndpointOrThrow(id);
        endpoint.setName(request.getName());
        endpoint.setUrl(request.getUrl());
        endpoint.setEnabled(request.getEnabled());

        endpoint.getSubscriptions().clear();
        for (String eventType : request.getEventTypes()) {
            WebhookEndpointSubscriptionEntity sub = WebhookEndpointSubscriptionEntity.builder()
                    .endpoint(endpoint)
                    .eventType(eventType)
                    .build();
            endpoint.getSubscriptions().add(sub);
        }

        return mapToResponse(webhookEndpointRepository.save(endpoint));
    }

    @Transactional
    public WebhookSecretResponseDTO rotateSecret(UUID id) {
        WebhookEndpointEntity endpoint = findEndpointOrThrow(id);
        String newSecret = generateSecret();
        endpoint.setSigningSecret(newSecret);
        webhookEndpointRepository.save(endpoint);
        return WebhookSecretResponseDTO.builder()
                .id(endpoint.getId())
                .signingSecret(newSecret)
                .build();
    }

    @Transactional
    public void sendTestEvent(UUID id) {
        WebhookEndpointEntity endpoint = findEndpointOrThrow(id);
        webhookEventService.createWebhookTestEvent(new WebhookTestEventFacts(endpoint.getId()));
    }

    @Transactional(readOnly = true)
    public List<WebhookDeliveryResponseDTO> listDeliveries(UUID endpointId, WebhookDeliveryStatus status, String eventType, int limit) {
        int pageSize = Math.min(limit, MAX_DELIVERY_PAGE_SIZE);
        PageRequest page = PageRequest.of(0, pageSize);

        List<WebhookDeliveryEntity> deliveries;
        if (endpointId != null && status != null && eventType != null) {
            deliveries = webhookDeliveryRepository.findByEndpointIdAndStatusAndEventTypeOrderByCreatedAtDesc(endpointId, status, eventType, page);
        } else if (endpointId != null && status != null) {
            deliveries = webhookDeliveryRepository.findByEndpointIdAndStatusOrderByCreatedAtDesc(endpointId, status, page);
        } else if (endpointId != null && eventType != null) {
            deliveries = webhookDeliveryRepository.findByEndpointIdAndEventTypeOrderByCreatedAtDesc(endpointId, eventType, page);
        } else if (status != null && eventType != null) {
            deliveries = webhookDeliveryRepository.findByStatusAndEventTypeOrderByCreatedAtDesc(status, eventType, page);
        } else if (endpointId != null) {
            deliveries = webhookDeliveryRepository.findByEndpointId(endpointId, page);
        } else if (status != null) {
            deliveries = webhookDeliveryRepository.findByStatusOrderByCreatedAtDesc(status, page);
        } else if (eventType != null) {
            deliveries = webhookDeliveryRepository.findByEventTypeOrderByCreatedAtDesc(eventType, page);
        } else {
            deliveries = webhookDeliveryRepository.findAll(page).getContent();
        }

        return deliveries.stream()
                .map(this::mapToDeliveryResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public WebhookDeliveryResponseDTO getDelivery(UUID id) {
        WebhookDeliveryEntity delivery = webhookDeliveryRepository.findByIdWithEvent(id)
                .orElseThrow(() -> new AratiriException("Webhook delivery not found", HttpStatus.NOT_FOUND.value()));
        return mapToDeliveryResponse(delivery);
    }

    @Transactional
    public void retryDelivery(UUID id) {
        WebhookDeliveryEntity delivery = webhookDeliveryRepository.findById(id)
                .orElseThrow(() -> new AratiriException("Webhook delivery not found", HttpStatus.NOT_FOUND.value()));
        if (delivery.getStatus() == WebhookDeliveryStatus.SUCCEEDED) {
            throw new AratiriException("Cannot retry a succeeded delivery", HttpStatus.BAD_REQUEST.value());
        }
        webhookDeliveryLifecycle.resetForManualRetry(delivery);
        log.info("Manually retried webhook delivery id={}", id);
    }

    private WebhookEndpointEntity findEndpointOrThrow(UUID id) {
        return webhookEndpointRepository.findById(id)
                .orElseThrow(() -> new AratiriException("Webhook endpoint not found", HttpStatus.NOT_FOUND.value()));
    }

    private WebhookEndpointResponseDTO mapToResponse(WebhookEndpointEntity endpoint) {
        return WebhookEndpointResponseDTO.builder()
                .id(endpoint.getId())
                .name(endpoint.getName())
                .url(endpoint.getUrl())
                .eventTypes(endpoint.getSubscriptions().stream()
                        .map(WebhookEndpointSubscriptionEntity::getEventType)
                        .collect(Collectors.toSet()))
                .enabled(endpoint.getEnabled())
                .createdAt(endpoint.getCreatedAt())
                .updatedAt(endpoint.getUpdatedAt())
                .lastSuccessAt(endpoint.getLastSuccessAt())
                .lastFailureAt(endpoint.getLastFailureAt())
                .build();
    }

    private WebhookDeliveryResponseDTO mapToDeliveryResponse(WebhookDeliveryEntity delivery) {
        return WebhookDeliveryResponseDTO.builder()
                .id(delivery.getId())
                .eventId(delivery.getEventId())
                .endpointId(delivery.getEndpointId())
                .status(delivery.getStatus().name())
                .attemptCount(delivery.getAttemptCount())
                .nextAttemptAt(delivery.getNextAttemptAt())
                .responseStatus(delivery.getResponseStatus())
                .lastError(delivery.getLastError())
                .createdAt(delivery.getCreatedAt())
                .updatedAt(delivery.getUpdatedAt())
                .deliveredAt(delivery.getDeliveredAt())
                .eventType(delivery.getEventType())
                .build();
    }

    private String generateSecret() {
        byte[] bytes = new byte[SECRET_LENGTH_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
