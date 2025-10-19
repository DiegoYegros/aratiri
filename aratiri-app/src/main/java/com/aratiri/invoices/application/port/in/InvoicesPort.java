package com.aratiri.invoices.application.port.in;

import com.aratiri.dto.invoices.DecodedInvoicetDTO;
import com.aratiri.dto.invoices.GenerateInvoiceDTO;
import lnrpc.Invoice;

import java.util.Optional;

public interface InvoicesPort {
    GenerateInvoiceDTO generateInvoice(long satsAmount, String memo, String userId);

    GenerateInvoiceDTO generateInvoice(String alias, long satsAmount, String memo);

    DecodedInvoicetDTO decodeAratiriPaymentRequest(String paymentRequest, String userId);

    Optional<Invoice> lookupInvoice(String paymentHash);

    DecodedInvoicetDTO decodePaymentRequest(String invoice);

    boolean existsSettledInvoiceByPaymentHash(String paymentHash);
}