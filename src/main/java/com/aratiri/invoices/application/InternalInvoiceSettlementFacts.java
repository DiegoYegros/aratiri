package com.aratiri.invoices.application;

import com.aratiri.invoices.domain.LightningInvoice;

import java.time.LocalDateTime;
import java.util.Objects;

public record InternalInvoiceSettlementFacts(
        String paymentHash,
        String receiverId,
        long amountPaidSats,
        LocalDateTime settledAt,
        String memo
) {

    public InternalInvoiceSettlementFacts {
        Objects.requireNonNull(paymentHash, "paymentHash must not be null");
        Objects.requireNonNull(receiverId, "receiverId must not be null");
    }

    public static InternalInvoiceSettlementFacts from(LightningInvoice invoice) {
        return new InternalInvoiceSettlementFacts(
                invoice.paymentHash(),
                invoice.userId(),
                invoice.amountPaidSats(),
                invoice.settledAt(),
                invoice.memo()
        );
    }
}
