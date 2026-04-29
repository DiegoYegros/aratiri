package com.aratiri.webhooks.application;

import com.aratiri.infrastructure.persistence.jpa.entity.WebhookDeliveryEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.WebhookEndpointEntity;

import java.util.List;

public interface WebhookDeliveryLifecycle {

    List<WebhookDeliveryEntity> claimRunnableDeliveries();

    void attemptDelivery(WebhookDeliveryEntity delivery);

    void recordDeliverySuccess(WebhookDeliveryEntity delivery, WebhookEndpointEntity endpoint, WebhookSendResult result);

    void scheduleRetryOrFail(WebhookDeliveryEntity delivery);

    void resetForManualRetry(WebhookDeliveryEntity delivery);
}
