package com.aratiri.aratiri.enums;

public enum KafkaTopics {
    INVOICE_SETTLED("invoice.settled");

    private final String code;

    KafkaTopics(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}