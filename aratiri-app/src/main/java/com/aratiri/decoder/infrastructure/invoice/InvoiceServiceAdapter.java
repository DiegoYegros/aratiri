package com.aratiri.decoder.infrastructure.invoice;

import com.aratiri.decoder.application.port.out.InvoiceDecodingPort;
import com.aratiri.dto.invoices.DecodedInvoicetDTO;
import com.aratiri.service.InvoiceService;
import org.springframework.stereotype.Component;

@Component("decoderInvoiceServiceAdapter")
public class InvoiceServiceAdapter implements InvoiceDecodingPort {

    private final InvoiceService invoiceService;

    public InvoiceServiceAdapter(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @Override
    public DecodedInvoicetDTO decodeInvoice(String paymentRequest) {
        return invoiceService.decodePaymentRequest(paymentRequest);
    }
}
