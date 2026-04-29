package com.aratiri.webhooks.application;

public record WebhookSendResult(int statusCode, String body) {

    public boolean successful() {
        return statusCode >= 200 && statusCode < 300;
    }
}
