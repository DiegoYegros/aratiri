package com.aratiri.aratiri.job;

import com.aratiri.aratiri.entity.OutboxEventEntity;
import com.aratiri.aratiri.enums.KafkaTopics;
import com.aratiri.aratiri.producer.OutboxEventProducer;
import com.aratiri.aratiri.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventJob {
    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventProducer outboxEventProducer;

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void processOutboxEvents() {
        List<OutboxEventEntity> pendingEvents = outboxEventRepository.findByProcessedAtIsNullOrderByCreatedAtAsc();
        if (pendingEvents.isEmpty()) {
            return;
        }
        log.info("Found {} pending events in outbox to process.", pendingEvents.size());
        for (OutboxEventEntity event : pendingEvents) {
            try {
                String eventType = event.getEventType();
                if (KafkaTopics.INVOICE_SETTLED.getCode().equals(eventType)) {
                    outboxEventProducer.sendEvent(KafkaTopics.INVOICE_SETTLED, event.getPayload());
                } else if (KafkaTopics.PAYMENT_INITIATED.getCode().equals(eventType)) {
                    outboxEventProducer.sendEvent(KafkaTopics.PAYMENT_INITIATED, event.getPayload());
                } else if (KafkaTopics.ONCHAIN_PAYMENT_INITIATED.getCode().equals(eventType)) {
                    outboxEventProducer.sendEvent(KafkaTopics.ONCHAIN_PAYMENT_INITIATED, event.getPayload());
                } else if (KafkaTopics.INTERNAL_TRANSFER_INITIATED.getCode().equals(eventType)) {
                    outboxEventProducer.sendEvent(KafkaTopics.INTERNAL_TRANSFER_INITIATED, event.getPayload());
                } else if (KafkaTopics.PAYMENT_SENT.getCode().equals(eventType)) {
                    outboxEventProducer.sendEvent(KafkaTopics.PAYMENT_SENT, event.getPayload());
                } else if (KafkaTopics.ONCHAIN_TRANSACTION_RECEIVED.getCode().equals(eventType)) {
                    outboxEventProducer.sendEvent(KafkaTopics.ONCHAIN_TRANSACTION_RECEIVED, event.getPayload());
                } else {
                    log.error("Couldn't find a Kafka topic for event type: [{}] -- Ignoring.", eventType);
                    continue;
                }

                event.setProcessedAt(Instant.now());
                outboxEventRepository.save(event);
            } catch (Exception e) {
                log.error("Error processing outbox event ID: {}. It will be retried.", event.getId(), e);
            }
        }
    }
}