package com.aratiri.aratiri.enums;

import lombok.Getter;

@Getter
public enum KafkaTopics {
    INVOICE_SETTLED("invoice.settled"),
    INTERNAL_TRANSFER_INITIATED("internal.transfer.initiated"),
    INTERNAL_TRANSFER_COMPLETED("internal.transfer.completed");
    private final String code;

    KafkaTopics(String code) {
        this.code = code;
    }

}