package com.aratiri.invoices.application.port.out;

import com.aratiri.invoices.domain.DecodedLightningInvoice;
import com.aratiri.invoices.domain.LightningInvoiceCreation;
import com.aratiri.invoices.domain.LightningNodeInvoice;

import java.util.Optional;

public interface LightningNodePort {

    LightningInvoiceCreation createInvoice(long satsAmount, String memo, byte[] preimage, byte[] hash);

    DecodedLightningInvoice decodePaymentRequest(String paymentRequest);

    Optional<LightningNodeInvoice> lookupInvoice(String paymentHash);
}
