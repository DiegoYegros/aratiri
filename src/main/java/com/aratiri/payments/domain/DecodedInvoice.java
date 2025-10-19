package com.aratiri.payments.domain;

public record DecodedInvoice(
        String paymentHash,
        long amountSatoshis,
        String description
) {
}
