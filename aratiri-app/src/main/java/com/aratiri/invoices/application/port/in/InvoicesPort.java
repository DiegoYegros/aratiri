package com.aratiri.invoices.application.port.in;

import com.aratiri.invoices.application.dto.DecodedInvoicetDTO;
import com.aratiri.invoices.application.dto.GenerateInvoiceDTO;
import com.aratiri.invoices.domain.LightningNodeInvoice;

import java.util.Optional;

public interface InvoicesPort {
    GenerateInvoiceDTO generateInvoice(long satsAmount, String memo, String userId);

    GenerateInvoiceDTO generateInvoice(String alias, long satsAmount, String memo);

    DecodedInvoicetDTO decodeAratiriPaymentRequest(String paymentRequest, String userId);

    Optional<LightningNodeInvoice> lookupInvoice(String paymentHash);

    DecodedInvoicetDTO decodePaymentRequest(String invoice);

    boolean existsSettledInvoiceByPaymentHash(String paymentHash);
}
