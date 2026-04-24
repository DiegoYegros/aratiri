package com.aratiri.infrastructure.messaging;

import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
public enum KafkaTopics {
    INVOICE_SETTLED(KafkaTopicNames.INVOICE_SETTLED),
    INTERNAL_TRANSFER_INITIATED(KafkaTopicNames.INTERNAL_TRANSFER_INITIATED),
    INTERNAL_TRANSFER_COMPLETED(KafkaTopicNames.INTERNAL_TRANSFER_COMPLETED),
    INTERNAL_INVOICE_CANCEL(KafkaTopicNames.INTERNAL_INVOICE_CANCEL),
    PAYMENT_SENT(KafkaTopicNames.PAYMENT_SENT),
    PAYMENT_INITIATED(KafkaTopicNames.PAYMENT_INITIATED),
    ONCHAIN_PAYMENT_INITIATED(KafkaTopicNames.ONCHAIN_PAYMENT_INITIATED),
    ONCHAIN_TRANSACTION_RECEIVED(KafkaTopicNames.ONCHAIN_TRANSACTION_RECEIVED);

    private static final Map<String, KafkaTopics> CODE_TO_TOPIC = Arrays.stream(values())
        .collect(Collectors.toUnmodifiableMap(KafkaTopics::getCode, Function.identity()));

    private final String code;

    KafkaTopics(String code) {
        this.code = code;
    }

    public static Optional<KafkaTopics> fromCode(String code) {
        return Optional.ofNullable(CODE_TO_TOPIC.get(code));
    }
}
