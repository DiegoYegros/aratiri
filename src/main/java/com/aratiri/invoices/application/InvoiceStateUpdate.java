package com.aratiri.invoices.application;

import java.util.Objects;

public record InvoiceStateUpdate(
        String paymentRequest,
        State state,
        long amountPaidSat
) {

    public InvoiceStateUpdate {
        Objects.requireNonNull(paymentRequest, "paymentRequest must not be null");
        Objects.requireNonNull(state, "state must not be null");
    }

    public enum State {
        OPEN,
        SETTLED,
        CANCELED,
        ACCEPTED,
        UNKNOWN
    }
}
