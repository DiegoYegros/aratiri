package com.aratiri.infrastructure.messaging.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaConsumerPolicy {

    private final JsonMapper jsonMapper;

    public <T> void deserializeHandleAndAcknowledge(
            String message,
            Class<T> eventType,
            Acknowledgment acknowledgment,
            String failureMessage,
            Consumer<T> handler
    ) {
        try {
            T event = jsonMapper.readValue(message, eventType);
            handler.accept(event);
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("{}: {}", failureMessage, message, e);
            throw new IllegalStateException(failureMessage, e);
        }
    }
}
