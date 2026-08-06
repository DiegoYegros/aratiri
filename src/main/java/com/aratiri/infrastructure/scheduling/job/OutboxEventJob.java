package com.aratiri.infrastructure.scheduling.job;

import com.aratiri.infrastructure.messaging.KafkaTopics;
import com.aratiri.infrastructure.messaging.producer.OutboxEventProducer;
import com.aratiri.infrastructure.persistence.jpa.entity.OutboxEventEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventJob {

    private final OutboxEventClaimer outboxEventClaimer;
    private final OutboxEventProducer outboxEventProducer;

    @Scheduled(fixedDelayString = "${aratiri.outbox.fixed-delay-ms:1000}")
    public void processOutboxEvents() {
        List<OutboxEventEntity> claimed = outboxEventClaimer.claimBatch();
        if (claimed.isEmpty()) {
            return;
        }
        log.info("Found {} pending events in outbox to process.", claimed.size());
        for (OutboxEventEntity event : claimed) {
            Optional<KafkaTopics> topic = KafkaTopics.fromCode(event.getEventType());
            if (topic.isEmpty()) {
                String error = "Unknown outbox event type: " + event.getEventType();
                int updated = outboxEventClaimer.markInvalid(event, error);
                if (updated == 0) {
                    log.warn("Skipping INVALID mark for event ID {}: claim fence missed.", event.getId());
                } else {
                    log.error("{}. Marked event ID {} as INVALID.", error, event.getId());
                }
                continue;
            }
            try {
                outboxEventProducer.sendEvent(topic.get(), event.getAggregateId(), event.getPayload());
                int updated = outboxEventClaimer.markPublished(event);
                if (updated == 0) {
                    log.warn("Skipping PUBLISHED mark for event ID {}: claim fence missed.", event.getId());
                }
            } catch (Exception e) {
                int updated = outboxEventClaimer.markPublishFailed(event, e.getMessage());
                if (updated == 0) {
                    log.warn("Skipping FAILED mark for event ID {}: claim fence missed.", event.getId());
                } else {
                    log.error("Error processing outbox event ID: {}. It will be retried.", event.getId(), e);
                }
            }
        }
    }
}
