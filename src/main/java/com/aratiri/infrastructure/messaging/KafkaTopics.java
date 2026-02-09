package com.aratiri.infrastructure.messaging;

import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
public enum KafkaTopics {
    INVOICE_SETTLED("invoice.settled"),
    INTERNAL_TRANSFER_INITIATED("internal.transfer.initiated"),
    INTERNAL_TRANSFER_COMPLETED("internal.transfer.completed"),
    INTERNAL_INVOICE_CANCEL("internal.invoice.cancel"),
    PAYMENT_SENT("payment.sent"),
    PAYMENT_INITIATED("payment.initiated"),
    ONCHAIN_PAYMENT_INITIATED("onchain.payment.initiated"),
    ONCHAIN_TRANSACTION_RECEIVED("onchain.transaction.received");

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
