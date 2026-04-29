package com.aratiri.infrastructure.messaging.outbox;

import com.aratiri.infrastructure.messaging.KafkaTopics;

public interface CommittedEvent {

    String aggregateType();

    String aggregateId();

    KafkaTopics topic();

    Object payload();
}
