package com.aratiri.payments.infrastructure.invoice;

import com.aratiri.payments.application.port.out.InvoicesPort;
import com.aratiri.payments.domain.DecodedInvoice;
import com.aratiri.service.InvoiceService;
import org.springframework.stereotype.Component;

@Component
public class InvoiceServiceAdapter implements InvoicesPort {

    private final InvoiceService invoiceService;

    public InvoiceServiceAdapter(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @Override
    public DecodedInvoice decodeInvoice(String paymentRequest) {
        var decoded = invoiceService.decodePaymentRequest(paymentRequest);
        return new DecodedInvoice(
                decoded.getPaymentHash(),
                decoded.getNumSatoshis(),
                decoded.getDescription()
        );
    }

    @Override
    public boolean existsSettledInvoice(String paymentHash) {
        return invoiceService.existsSettledInvoiceByPaymentHash(paymentHash);
    }
}
