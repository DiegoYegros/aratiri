package com.aratiri.payments.application.port.out;

import com.aratiri.payments.domain.InternalLightningInvoice;

import java.util.Optional;

public interface LightningInvoicePort {

    Optional<InternalLightningInvoice> findByPaymentHash(String paymentHash);
}
