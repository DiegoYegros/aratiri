package com.aratiri.infrastructure.scheduling.job;

import com.aratiri.infrastructure.persistence.jpa.entity.WebhookDeliveryEntity;
import com.aratiri.webhooks.application.WebhookDeliveryLifecycle;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class WebhookDeliveryJob {

    private final WebhookDeliveryLifecycle webhookDeliveryLifecycle;

    public WebhookDeliveryJob(WebhookDeliveryLifecycle webhookDeliveryLifecycle) {
        this.webhookDeliveryLifecycle = webhookDeliveryLifecycle;
    }

    @Scheduled(fixedDelayString = "${aratiri.webhooks.delivery.fixed-delay-ms:5000}")
    public void processDeliveries() {
        List<WebhookDeliveryEntity> claimed = webhookDeliveryLifecycle.claimRunnableDeliveries();
        for (WebhookDeliveryEntity delivery : claimed) {
            webhookDeliveryLifecycle.attemptDelivery(delivery);
        }
    }
}
