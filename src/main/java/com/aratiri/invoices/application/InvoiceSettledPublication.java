package com.aratiri.invoices.application;

import com.aratiri.invoices.application.event.InvoiceSettledEvent;

import java.util.Objects;

public record InvoiceSettledPublication(
        String invoiceId,
        InvoiceSettledEvent event
) {

    public InvoiceSettledPublication {
        Objects.requireNonNull(invoiceId, "invoiceId must not be null");
        Objects.requireNonNull(event, "event must not be null");
    }
}
