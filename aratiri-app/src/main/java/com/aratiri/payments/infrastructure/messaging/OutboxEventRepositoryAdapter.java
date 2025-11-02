package com.aratiri.payments.infrastructure.messaging;

import com.aratiri.infrastructure.persistence.jpa.entity.OutboxEventEntity;
import com.aratiri.infrastructure.persistence.jpa.repository.OutboxEventRepository;
import com.aratiri.payments.application.port.out.OutboxEventPort;
import com.aratiri.payments.domain.OutboxMessage;
import org.springframework.stereotype.Component;

@Component
public class OutboxEventRepositoryAdapter implements OutboxEventPort {

    private final OutboxEventRepository outboxEventRepository;

    public OutboxEventRepositoryAdapter(OutboxEventRepository outboxEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
    }

    @Override
    public void save(OutboxMessage message) {
        OutboxEventEntity entity = OutboxEventEntity.builder()
                .aggregateType(message.aggregateType())
                .aggregateId(message.aggregateId())
                .eventType(message.eventType())
                .payload(message.payload())
                .build();
        outboxEventRepository.save(entity);
    }
}
