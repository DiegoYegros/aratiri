package com.aratiri.aratiri.service;

import com.aratiri.aratiri.dto.invoices.DecodedInvoicetDTO;
import com.aratiri.aratiri.dto.invoices.GenerateInvoiceDTO;
import com.aratiri.aratiri.dto.invoices.PayInvoiceDTO;
import jakarta.validation.constraints.NotBlank;

public interface InvoiceService {
    GenerateInvoiceDTO generateInvoice(long satsAmount, String memo, String userId);
    GenerateInvoiceDTO generateInvoice(String alias, long satsAmount, String memo);

    PayInvoiceDTO payInvoice(String paymentRquest);
    DecodedInvoicetDTO decodeAratiriPaymentRequest(String paymentRequest, String userId);

    DecodedInvoicetDTO decodePaymentRequest(String invoice);
}