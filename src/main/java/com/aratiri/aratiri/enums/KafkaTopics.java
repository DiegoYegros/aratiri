package com.aratiri.aratiri.enums;

import lombok.Getter;

@Getter
public enum KafkaTopics {
    INVOICE_SETTLED("invoice.settled");

    private final String code;

    KafkaTopics(String code) {
        this.code = code;
    }

}