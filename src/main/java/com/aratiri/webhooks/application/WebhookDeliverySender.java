package com.aratiri.webhooks.application;

import com.aratiri.infrastructure.persistence.jpa.entity.WebhookDeliveryEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.WebhookEndpointEntity;

import java.io.IOException;

public interface WebhookDeliverySender {

    WebhookSendResult send(WebhookDeliveryEntity delivery, WebhookEndpointEntity endpoint)
            throws IOException, InterruptedException;
}
