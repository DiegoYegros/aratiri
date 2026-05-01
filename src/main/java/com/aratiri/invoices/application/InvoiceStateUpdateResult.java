package com.aratiri.invoices.application;

import java.util.Optional;
import java.util.Objects;

public record InvoiceStateUpdateResult(
        boolean stateChanged,
        Optional<InvoiceSettledPublication> settledPublication
) {

    public InvoiceStateUpdateResult {
        Objects.requireNonNull(settledPublication, "settledPublication must not be null");
    }

    public static InvoiceStateUpdateResult ignored() {
        return new InvoiceStateUpdateResult(false, Optional.empty());
    }

    public static InvoiceStateUpdateResult changed() {
        return new InvoiceStateUpdateResult(true, Optional.empty());
    }

    public static InvoiceStateUpdateResult settled(InvoiceSettledPublication publication) {
        return new InvoiceStateUpdateResult(true, Optional.of(publication));
    }
}
