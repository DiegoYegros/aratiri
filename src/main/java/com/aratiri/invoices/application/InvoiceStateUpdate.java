package com.aratiri.invoices.application;

import java.util.Objects;

public record InvoiceStateUpdate(
        String paymentRequest,
        String paymentHash,
        State state,
        long amountPaidSat
) {

    public InvoiceStateUpdate {
        Objects.requireNonNull(state, "state must not be null");
    }

    /** Backwards-compatible constructor used by older call sites/tests. */
    public InvoiceStateUpdate(String paymentRequest, State state, long amountPaidSat) {
        this(paymentRequest, null, state, amountPaidSat);
    }

    public enum State {
        OPEN,
        SETTLED,
        CANCELED,
        ACCEPTED,
        UNKNOWN
    }
}
