package com.aratiri.decoder.application.port.out;

import com.aratiri.dto.invoices.DecodedInvoicetDTO;

public interface InvoiceDecodingPort {

    DecodedInvoicetDTO decodeInvoice(String paymentRequest);
}
