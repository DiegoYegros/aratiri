package com.aratiri.infrastructure.scheduling.job;

import com.aratiri.infrastructure.persistence.jpa.entity.WebhookDeliveryEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.WebhookDeliveryStatus;
import com.aratiri.infrastructure.persistence.jpa.repository.WebhookDeliveryRepository;
import com.aratiri.webhooks.application.WebhookDeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebhookDeliveryJob {

    private static final String LOCKED_BY_PREFIX = "webhook-worker-";
    private static final long LEASE_SECONDS = 120;
    private static final int BATCH_SIZE = 50;

    private final WebhookDeliveryRepository webhookDeliveryRepository;
    private final WebhookDeliveryService webhookDeliveryService;

    @Scheduled(fixedDelayString = "${aratiri.webhooks.delivery.fixed-delay-ms:5000}")
    public void processDeliveries() {
        List<WebhookDeliveryEntity> claimed = claimBatch();
        for (WebhookDeliveryEntity delivery : claimed) {
            try {
                boolean success = webhookDeliveryService.deliver(delivery);
                if (success) {
                    webhookDeliveryRepository.save(delivery);
                } else {
                    webhookDeliveryService.scheduleRetryOrFail(delivery);
                }
            } catch (Exception e) {
                log.error("Unexpected error processing webhook delivery id={}", delivery.getId(), e);
                try {
                    webhookDeliveryService.scheduleRetryOrFail(delivery);
                } catch (Exception ex) {
                    log.error("Failed to schedule retry for delivery id={}", delivery.getId(), ex);
                }
            }
        }
    }

    @Transactional
    List<WebhookDeliveryEntity> claimBatch() {
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
}
