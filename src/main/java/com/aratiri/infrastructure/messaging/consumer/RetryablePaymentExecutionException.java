package com.aratiri.infrastructure.messaging.consumer;

/**
 * Signals an execution outcome that must be retried by the Kafka retry-topic machinery; the
 * transaction-state and LND-state checks on the next delivery decide the final outcome.
 */
public class RetryablePaymentExecutionException extends RuntimeException {

    public RetryablePaymentExecutionException(String message) {
        super(message);
    }

    public RetryablePaymentExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
