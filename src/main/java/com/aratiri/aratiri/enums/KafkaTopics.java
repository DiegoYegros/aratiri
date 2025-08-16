package com.aratiri.aratiri.enums;

import lombok.Getter;

@Getter
public enum KafkaTopics {
    INVOICE_SETTLED("invoice.settled"),
    INTERNAL_TRANSFER_INITIATED("internal.transfer.initiated"),
    INTERNAL_TRANSFER_COMPLETED("internal.transfer.completed"),
    PAYMENT_SENT("payment.sent"),
    PAYMENT_INITIATED("payment.initiated"),
    ONCHAIN_PAYMENT_INITIATED("onchain.payment.initiated"),
    ONCHAIN_TRANSACTION_RECEIVED("onchain.transaction.received");

    private final String code;

    KafkaTopics(String code) {
        this.code = code;
    }

}