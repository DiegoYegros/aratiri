package com.aratiri.infrastructure.scheduling.job;

import com.aratiri.infrastructure.messaging.KafkaTopics;
import com.aratiri.infrastructure.messaging.producer.OutboxEventProducer;
import com.aratiri.infrastructure.persistence.jpa.entity.OutboxEventEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.OutboxPublishStatus;
import com.aratiri.infrastructure.persistence.jpa.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventJob {
    private static final Set<OutboxPublishStatus> PUBLISHABLE_STATUSES =
            Set.of(OutboxPublishStatus.PENDING, OutboxPublishStatus.FAILED);

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventProducer outboxEventProducer;

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void processOutboxEvents() {
        List<OutboxEventEntity> pendingEvents =
                outboxEventRepository.findPublishableEvents(Instant.now(), PUBLISHABLE_STATUSES);
        if (pendingEvents.isEmpty()) {
            return;
        }
        log.info("Found {} pending events in outbox to process.", pendingEvents.size());
        for (OutboxEventEntity event : pendingEvents) {
            try {
                Optional<KafkaTopics> topic = KafkaTopics.fromCode(event.getEventType());
                if (topic.isEmpty()) {
                    String error = "Unknown outbox event type: " + event.getEventType();
                    event.markInvalid(error);
                    outboxEventRepository.save(event);
                    log.error("{}. Marked event ID {} as INVALID.", error, event.getId());
                    continue;
                }
                outboxEventProducer.sendEvent(topic.get(), event.getPayload());
                event.markPublished(Instant.now());
                outboxEventRepository.save(event);
            } catch (Exception e) {
                event.markPublishFailed(e.getMessage(), Instant.now());
                outboxEventRepository.save(event);
                log.error("Error processing outbox event ID: {}. It will be retried.", event.getId(), e);
            }
        }
    }
}
