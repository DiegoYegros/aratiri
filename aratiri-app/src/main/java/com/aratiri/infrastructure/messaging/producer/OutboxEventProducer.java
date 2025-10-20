package com.aratiri.infrastructure.messaging.producer;

import com.aratiri.infrastructure.messaging.KafkaTopics;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OutboxEventProducer {

    private final Logger logger = LoggerFactory.getLogger(OutboxEventProducer.class);
    private final KafkaTemplate<String, String> kafkaTemplate;

    public void sendEvent(KafkaTopics topic, String payload) {
        try {
            kafkaTemplate.send(topic.getCode(), payload);
            logger.info("Successfully sent event from outbox to Kafka topic: {}", topic.getCode());
        } catch (Exception e) {
            logger.error("Failed to send event from outbox to Kafka topic: {}", topic.getCode(), e);
            throw e;
        }
    }
}