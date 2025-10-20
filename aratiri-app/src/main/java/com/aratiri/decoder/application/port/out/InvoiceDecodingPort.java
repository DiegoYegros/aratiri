package com.aratiri.decoder.application.port.out;

import com.aratiri.invoices.application.dto.DecodedInvoicetDTO;

public interface InvoiceDecodingPort {

    DecodedInvoicetDTO decodeInvoice(String paymentRequest);
}
