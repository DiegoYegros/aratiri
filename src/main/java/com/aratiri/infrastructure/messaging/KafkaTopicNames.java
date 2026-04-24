package com.aratiri.infrastructure.messaging;

/**
 * Canonical Kafka topic strings for {@code @KafkaListener} and header comparisons.
 * Outbox and producers use {@link KafkaTopics}, whose {@link KafkaTopics#getCode()} values match these constants.
 */
public final class KafkaTopicNames {

    private KafkaTopicNames() {}

    public static final String INVOICE_SETTLED = "invoice.settled";
    public static final String INTERNAL_TRANSFER_INITIATED = "internal.transfer.initiated";
    public static final String INTERNAL_TRANSFER_COMPLETED = "internal.transfer.completed";
    public static final String INTERNAL_INVOICE_CANCEL = "internal.invoice.cancel";
    public static final String PAYMENT_SENT = "payment.sent";
    public static final String PAYMENT_INITIATED = "payment.initiated";
    public static final String ONCHAIN_PAYMENT_INITIATED = "onchain.payment.initiated";
    public static final String ONCHAIN_TRANSACTION_RECEIVED = "onchain.transaction.received";
}
