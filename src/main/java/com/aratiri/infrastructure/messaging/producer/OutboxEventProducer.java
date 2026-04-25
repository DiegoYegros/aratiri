package com.aratiri.infrastructure.messaging.producer;

import com.aratiri.infrastructure.messaging.KafkaTopics;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
@RequiredArgsConstructor
public class OutboxEventProducer {

    private final Logger logger = LoggerFactory.getLogger(OutboxEventProducer.class);
    private final KafkaTemplate<String, String> kafkaTemplate;

    public void sendEvent(KafkaTopics topic, String payload) {
        try {
            kafkaTemplate.send(topic.getCode(), payload).get(10, TimeUnit.SECONDS);
            logger.info("Successfully sent event from outbox to Kafka topic: {}", topic.getCode());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while sending event from outbox to Kafka topic: " + topic.getCode(), e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Failed to send event from outbox to Kafka topic: " + topic.getCode(), e);
        }
    }
}
