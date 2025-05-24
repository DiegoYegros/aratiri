package com.aratiri.aratiri.service;

import com.aratiri.aratiri.dto.invoices.GenerateInvoiceDTO;
import com.aratiri.aratiri.dto.invoices.PayInvoiceDTO;

public interface InvoiceService {
    GenerateInvoiceDTO generateInvoice(long satsAmount, String memo);
    PayInvoiceDTO payInvoice(String paymentRquest);
}
