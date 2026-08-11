package com.aratiri.infrastructure.messaging;

import com.aratiri.errors.ApplicationException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

/**
 * Publishes domain events directly to Kafka. When called inside an active Spring transaction the
 * send is deferred to an {@code afterCommit} callback so no event is published for rolled-back
 * work; outside a transaction the send is immediate. Kafka send failures after commit are logged
 * and the event is lost — consumer-side idempotency and {@code TransactionReconciliationJob} are
 * the repair paths.
 */
@Service
@RequiredArgsConstructor
public class KafkaEventPublisher implements EventPublisher {

    private static final Logger logger = LoggerFactory.getLogger(KafkaEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;

    @Override
    public void publish(String topic, String key, Object payload) {
        String serialized = serialize(topic, payload);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(topic, key, serialized);
                }
            });
        } else {
            send(topic, key, serialized);
        }
    }

    private String serialize(String topic, Object payload) {
        try {
            return jsonMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new ApplicationException(
                    "Failed to serialize event payload for Kafka topic: " + topic,
                    HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    e
            );
        }
    }

    private void send(String topic, String key, String serialized) {
        try {
            kafkaTemplate.send(topic, key, serialized).whenComplete((result, ex) -> {
                if (ex != null) {
                    logger.error("Failed to send event to Kafka topic {} with key {}; event lost.", topic, key, ex);
                }
            });
        } catch (Exception e) {
            logger.error("Failed to send event to Kafka topic {} with key {}; event lost.", topic, key, e);
        }
    }
}
