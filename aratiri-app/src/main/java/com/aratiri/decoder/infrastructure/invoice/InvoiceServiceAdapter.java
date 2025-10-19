package com.aratiri.decoder.infrastructure.invoice;

import com.aratiri.decoder.application.port.out.InvoiceDecodingPort;
import com.aratiri.dto.invoices.DecodedInvoicetDTO;
import com.aratiri.invoices.application.port.in.InvoicesPort;
import org.springframework.stereotype.Component;

@Component("decoderInvoiceServiceAdapter")
public class InvoiceServiceAdapter implements InvoiceDecodingPort {

    private final InvoicesPort invoicesPort;

    public InvoiceServiceAdapter(InvoicesPort invoicesPort) {
        this.invoicesPort = invoicesPort;
    }

    @Override
    public DecodedInvoicetDTO decodeInvoice(String paymentRequest) {
        return invoicesPort.decodePaymentRequest(paymentRequest);
    }
}
