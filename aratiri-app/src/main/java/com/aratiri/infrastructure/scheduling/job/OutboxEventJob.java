package com.aratiri.infrastructure.scheduling.job;

import com.aratiri.infrastructure.messaging.KafkaTopics;
import com.aratiri.infrastructure.messaging.producer.OutboxEventProducer;
import com.aratiri.infrastructure.persistence.jpa.entity.OutboxEventEntity;
import com.aratiri.infrastructure.persistence.jpa.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventJob {
    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventProducer outboxEventProducer;

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void processOutboxEvents() {
        List<OutboxEventEntity> pendingEvents = outboxEventRepository.findAndLockByProcessedAtIsNullOrderByCreatedAtAsc();
        if (pendingEvents.isEmpty()) {
            return;
        }
        log.info("Found {} pending events in outbox to process.", pendingEvents.size());
        for (OutboxEventEntity event : pendingEvents) {
            try {
                Optional<KafkaTopics> topic = KafkaTopics.fromCode(event.getEventType());
                if (topic.isEmpty()) {
                    log.error("Could not find a Kafka topic for event type: [{}] -- Ignoring.", event.getEventType());
                    continue;
                }
                outboxEventProducer.sendEvent(topic.get(), event.getPayload());
                event.setProcessedAt(Instant.now());
                outboxEventRepository.save(event);
            } catch (Exception e) {
                log.error("Error processing outbox event ID: {}. It will be retried.", event.getId(), e);
            }
        }
    }
}