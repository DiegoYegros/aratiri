package com.aratiri.infrastructure.messaging;

public interface EventPublisher {

    void publish(String topic, String key, Object payload);
}
