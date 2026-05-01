package com.aratiri.invoices.application.port.out;

import com.aratiri.invoices.domain.LightningInvoice;

import java.util.Optional;

public interface LightningInvoicePersistencePort {

    LightningInvoice save(LightningInvoice invoice);

    Optional<LightningInvoice> findByPaymentHash(String paymentHash);

    Optional<LightningInvoice> findByPaymentRequest(String paymentRequest);

    Optional<LightningInvoice> findSettledByPaymentHash(String paymentHash);
}
