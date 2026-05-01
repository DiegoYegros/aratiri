package com.aratiri.invoices.application;

import java.util.Objects;

public record SettleInternalInvoiceCommand(
        String receiverId,
        String paymentHash,
        long amountSat
) {

    public SettleInternalInvoiceCommand {
        Objects.requireNonNull(receiverId, "receiverId must not be null");
        Objects.requireNonNull(paymentHash, "paymentHash must not be null");
    }
}
