package com.aratiri.webhooks.application;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WebhookSendResultTest {

    @Test
    void successful_200() {
        assertTrue(new WebhookSendResult(200, "OK").successful());
    }

    @Test
    void successful_299() {
        assertTrue(new WebhookSendResult(299, "").successful());
    }

    @Test
    void successful_3xx_notSuccessful() {
        assertFalse(new WebhookSendResult(300, "").successful());
    }

    @Test
    void successful_4xx_notSuccessful() {
        assertFalse(new WebhookSendResult(400, "").successful());
    }

    @Test
    void successful_5xx_notSuccessful() {
        assertFalse(new WebhookSendResult(500, "").successful());
    }

    @Test
    void constructorStoresValues() {
        WebhookSendResult result = new WebhookSendResult(201, "{\"ok\":true}");
        assertEquals(201, result.statusCode());
        assertEquals("{\"ok\":true}", result.body());
    }
}
