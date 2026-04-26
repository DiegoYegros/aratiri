package com.aratiri.webhooks.application;

import com.aratiri.infrastructure.persistence.jpa.entity.WebhookDeliveryEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.WebhookDeliveryStatus;
import com.aratiri.infrastructure.persistence.jpa.entity.WebhookEndpointEntity;
import com.aratiri.infrastructure.persistence.jpa.repository.WebhookDeliveryRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.WebhookEndpointRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookDeliveryService {

    private static final String USER_AGENT = "Aratiri-Webhooks/1.0";
    private static final String SIGNATURE_VERSION = "v1";
    private static final long CONNECT_TIMEOUT_SECONDS = 10;
    private static final long READ_TIMEOUT_SECONDS = 10;

    private static final long[] RETRY_DELAYS_MINUTES = {1, 5, 15, 60, 360};
    private static final int MAX_ATTEMPTS = 12;

    private final WebhookDeliveryRepository webhookDeliveryRepository;
    private final WebhookEndpointRepository webhookEndpointRepository;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public boolean deliver(WebhookDeliveryEntity delivery) {
        Optional<WebhookEndpointEntity> endpointOpt = webhookEndpointRepository.findById(delivery.getEndpointId());
        if (endpointOpt.isEmpty()) {
            log.warn("Endpoint not found for delivery id={}", delivery.getId());
            return false;
        }
        WebhookEndpointEntity endpoint = endpointOpt.get();
        if (!Boolean.TRUE.equals(endpoint.getEnabled())) {
            log.debug("Endpoint {} is disabled, skipping delivery {}", endpoint.getId(), delivery.getId());
            return false;
        }

        return sendDelivery(delivery, endpoint);
    }

    private boolean sendDelivery(WebhookDeliveryEntity delivery, WebhookEndpointEntity endpoint) {
        try {
            String payload = delivery.getPayload() != null ? delivery.getPayload() : "";
            String timestamp = String.valueOf(Instant.now().getEpochSecond());
            String signature = generateSignature(endpoint.getSigningSecret(), timestamp, delivery.getEventId().toString(), payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint.getUrl()))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", USER_AGENT)
                    .header("X-Aratiri-Event-Id", delivery.getEventId().toString())
                    .header("X-Aratiri-Event-Type", delivery.getEventType())
                    .header("X-Aratiri-Delivery-Id", delivery.getId().toString())
                    .header("X-Aratiri-Timestamp", timestamp)
                    .header("X-Aratiri-Signature", signature)
                    .timeout(Duration.ofSeconds(READ_TIMEOUT_SECONDS))
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            delivery.setResponseStatus(response.statusCode());
            delivery.setResponseBody(response.body());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                delivery.setAttemptCount(delivery.getAttemptCount() + 1);
                delivery.setStatus(WebhookDeliveryStatus.SUCCEEDED);
                delivery.setDeliveredAt(Instant.now());
                delivery.setLastError(null);
                delivery.setLockedBy(null);
                delivery.setLockedUntil(null);
                endpoint.setLastSuccessAt(Instant.now());
                webhookEndpointRepository.save(endpoint);
                webhookDeliveryRepository.save(delivery);
                log.info("Webhook delivery succeeded for deliveryId={}, endpoint={}, status={}",
                        delivery.getId(), endpoint.getId(), response.statusCode());
                return true;
            } else {
                String error = "HTTP " + response.statusCode();
                delivery.setLastError(error);
                log.warn("Webhook delivery failed for deliveryId={}, endpoint={}, status={}",
                        delivery.getId(), endpoint.getId(), response.statusCode());
                return false;
            }
        } catch (Exception e) {
            delivery.setLastError(e.getClass().getSimpleName() + ": " + e.getMessage());
            log.warn("Webhook delivery exception for deliveryId={}, endpoint={}: {}",
                    delivery.getId(), endpoint.getId(), e.getMessage());
            return false;
        }
    }

    public void scheduleRetryOrFail(WebhookDeliveryEntity delivery) {
        int completedAttempts = delivery.getAttemptCount();
        if (completedAttempts + 1 >= MAX_ATTEMPTS) {
            delivery.setStatus(WebhookDeliveryStatus.FAILED);
            delivery.setAttemptCount(completedAttempts + 1);
            log.info("Webhook delivery max attempts reached for deliveryId={}, marking FAILED", delivery.getId());
        } else {
            long delayMinutes = completedAttempts < RETRY_DELAYS_MINUTES.length
                    ? RETRY_DELAYS_MINUTES[completedAttempts]
                    : RETRY_DELAYS_MINUTES[RETRY_DELAYS_MINUTES.length - 1];
            delivery.setNextAttemptAt(Instant.now().plusSeconds(delayMinutes * 60));
            delivery.setAttemptCount(completedAttempts + 1);
            log.info("Webhook delivery scheduled retry {} for deliveryId={}, delay={}m", completedAttempts + 1, delivery.getId(), delayMinutes);
        }
        delivery.setLockedBy(null);
        delivery.setLockedUntil(null);
        webhookDeliveryRepository.save(delivery);

        WebhookEndpointEntity endpoint = webhookEndpointRepository.findById(delivery.getEndpointId()).orElse(null);
        if (endpoint != null) {
            endpoint.setLastFailureAt(Instant.now());
            webhookEndpointRepository.save(endpoint);
        }
    }

    public void resetForManualRetry(WebhookDeliveryEntity delivery) {
        delivery.setStatus(WebhookDeliveryStatus.PENDING);
        delivery.setNextAttemptAt(Instant.now());
        delivery.setLockedBy(null);
        delivery.setLockedUntil(null);
        delivery.setLastError(null);
        webhookDeliveryRepository.save(delivery);
        log.info("Webhook delivery manually retried for deliveryId={}", delivery.getId());
    }

    static String generateSignature(String secret, String timestamp, String eventId, String payload) {
        try {
            String signedPayload = timestamp + "." + eventId + "." + payload;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8));
            return SIGNATURE_VERSION + "=" + HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate webhook signature", e);
        }
    }
}
