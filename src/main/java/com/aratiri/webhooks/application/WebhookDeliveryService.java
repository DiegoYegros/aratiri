package com.aratiri.webhooks.application;

import com.aratiri.infrastructure.persistence.jpa.entity.WebhookDeliveryEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.WebhookDeliveryStatus;
import com.aratiri.infrastructure.persistence.jpa.entity.WebhookEndpointEntity;
import com.aratiri.infrastructure.persistence.jpa.repository.WebhookDeliveryRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.WebhookEndpointRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookDeliveryService implements WebhookDeliveryLifecycle {

    private static final String LOCKED_BY_PREFIX = "webhook-worker-";
    private static final long LEASE_SECONDS = 120;
    private static final int BATCH_SIZE = 50;
    private static final long[] RETRY_DELAYS_MINUTES = {1, 5, 15, 60, 360};
    private static final int MAX_ATTEMPTS = 12;

    private final WebhookDeliveryRepository webhookDeliveryRepository;
    private final WebhookEndpointRepository webhookEndpointRepository;
    private final WebhookDeliverySender webhookDeliverySender;

    @Override
    @Transactional
    public List<WebhookDeliveryEntity> claimRunnableDeliveries() {
        Instant now = Instant.now();
        Instant lockedUntil = now.plusSeconds(LEASE_SECONDS);
        String lockedBy = LOCKED_BY_PREFIX + Thread.currentThread().threadId();
        PageRequest page = PageRequest.of(0, BATCH_SIZE);

        List<WebhookDeliveryEntity> pending = webhookDeliveryRepository.findRunnableDeliveries(now, page);
        List<WebhookDeliveryEntity> claimed = new ArrayList<>();

        for (WebhookDeliveryEntity delivery : pending) {
            int updated = webhookDeliveryRepository.claimDelivery(
                    delivery.getId(),
                    lockedBy,
                    lockedUntil,
                    now
            );
            if (updated > 0) {
                delivery.setLockedBy(lockedBy);
                delivery.setLockedUntil(lockedUntil);
                claimed.add(delivery);
            }
        }
        return claimed;
    }

    @Override
    public void attemptDelivery(WebhookDeliveryEntity delivery) {
        try {
            boolean success = deliver(delivery);
            if (!success) {
                scheduleRetryOrFail(delivery);
            }
        } catch (Exception e) {
            log.error("Unexpected error processing webhook delivery id={}", delivery.getId(), e);
            try {
                delivery.setLastError("Unexpected: " + e.getMessage());
                scheduleRetryOrFail(delivery);
            } catch (Exception ex) {
                log.error("Failed to schedule retry for delivery id={}", delivery.getId(), ex);
            }
        }
    }

    public boolean deliver(WebhookDeliveryEntity delivery) {
        Optional<WebhookEndpointEntity> endpointOpt = webhookEndpointRepository.findById(delivery.getEndpointId());
        if (endpointOpt.isEmpty()) {
            log.warn("Endpoint not found for delivery id={}", delivery.getId());
            delivery.setLastError("Endpoint not found");
            return false;
        }
        WebhookEndpointEntity endpoint = endpointOpt.get();
        if (!Boolean.TRUE.equals(endpoint.getEnabled())) {
            log.debug("Endpoint {} is disabled, skipping delivery {}", endpoint.getId(), delivery.getId());
            delivery.setLastError("Endpoint disabled");
            return false;
        }

        return sendDelivery(delivery, endpoint);
    }

    private boolean sendDelivery(WebhookDeliveryEntity delivery, WebhookEndpointEntity endpoint) {
        try {
            WebhookSendResult result = webhookDeliverySender.send(delivery, endpoint);
            delivery.setResponseStatus(result.statusCode());
            delivery.setResponseBody(result.body());

            if (result.successful()) {
                recordDeliverySuccess(delivery, endpoint, result);
                return true;
            } else {
                String error = "HTTP " + result.statusCode();
                delivery.setLastError(error);
                log.warn("Webhook delivery failed for deliveryId={}, endpoint={}, status={}",
                        delivery.getId(), endpoint.getId(), result.statusCode());
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            delivery.setLastError("Interrupted: " + e.getMessage());
            log.warn("Webhook delivery interrupted for deliveryId={}, endpoint={}",
                    delivery.getId(), endpoint.getId());
            return false;
        } catch (IOException e) {
            delivery.setLastError("IOException: " + e.getMessage());
            log.warn("Webhook delivery IO exception for deliveryId={}, endpoint={}: {}",
                    delivery.getId(), endpoint.getId(), e.getMessage());
            return false;
        }
    }

    @Override
    public void recordDeliverySuccess(WebhookDeliveryEntity delivery, WebhookEndpointEntity endpoint, WebhookSendResult result) {
        Instant now = Instant.now();
        delivery.setResponseStatus(result.statusCode());
        delivery.setResponseBody(result.body());
        delivery.setAttemptCount(delivery.getAttemptCount() + 1);
        delivery.setStatus(WebhookDeliveryStatus.SUCCEEDED);
        delivery.setDeliveredAt(now);
        delivery.setLastError(null);
        delivery.setLockedBy(null);
        delivery.setLockedUntil(null);
        endpoint.setLastSuccessAt(now);
        webhookEndpointRepository.save(endpoint);
        webhookDeliveryRepository.save(delivery);
        log.info("Webhook delivery succeeded for deliveryId={}, endpoint={}, status={}",
                delivery.getId(), endpoint.getId(), result.statusCode());
    }

    @Override
    public void scheduleRetryOrFail(WebhookDeliveryEntity delivery) {
        Instant now = Instant.now();
        int completedAttempts = delivery.getAttemptCount();
        if (completedAttempts + 1 >= MAX_ATTEMPTS) {
            delivery.setStatus(WebhookDeliveryStatus.FAILED);
            delivery.setAttemptCount(completedAttempts + 1);
            log.info("Webhook delivery max attempts reached for deliveryId={}, marking FAILED", delivery.getId());
        } else {
            long delayMinutes = completedAttempts < RETRY_DELAYS_MINUTES.length
                    ? RETRY_DELAYS_MINUTES[completedAttempts]
                    : RETRY_DELAYS_MINUTES[RETRY_DELAYS_MINUTES.length - 1];
            delivery.setNextAttemptAt(now.plusSeconds(delayMinutes * 60));
            delivery.setAttemptCount(completedAttempts + 1);
            log.info("Webhook delivery scheduled retry {} for deliveryId={}, delay={}m", completedAttempts + 1, delivery.getId(), delayMinutes);
        }
        delivery.setLockedBy(null);
        delivery.setLockedUntil(null);
        webhookDeliveryRepository.save(delivery);

        WebhookEndpointEntity endpoint = webhookEndpointRepository.findById(delivery.getEndpointId()).orElse(null);
        if (endpoint != null) {
            endpoint.setLastFailureAt(now);
            webhookEndpointRepository.save(endpoint);
        }
    }

    @Override
    public void resetForManualRetry(WebhookDeliveryEntity delivery) {
        delivery.setStatus(WebhookDeliveryStatus.PENDING);
        delivery.setNextAttemptAt(Instant.now());
        delivery.setLockedBy(null);
        delivery.setLockedUntil(null);
        delivery.setLastError(null);
        webhookDeliveryRepository.save(delivery);
        log.info("Webhook delivery manually retried for deliveryId={}", delivery.getId());
    }
}
