package com.aratiri.payments.infrastructure.invoice;

import com.aratiri.payments.domain.DecodedInvoice;
import com.aratiri.invoices.application.port.in.InvoicesPort;
import org.springframework.stereotype.Component;

@Component("paymentsInvoiceServiceAdapter")
public class InvoiceServiceAdapter implements com.aratiri.payments.application.port.out.InvoicesPort {

    private final InvoicesPort invoicesPort;

    public InvoiceServiceAdapter(InvoicesPort invoicesPort) {
        this.invoicesPort = invoicesPort;
    }

    @Override
    public DecodedInvoice decodeInvoice(String paymentRequest) {
        var decoded = invoicesPort.decodePaymentRequest(paymentRequest);
        return new DecodedInvoice(
                decoded.getPaymentHash(),
                decoded.getNumSatoshis(),
                decoded.getDescription()
        );
    }

    @Override
    public boolean existsSettledInvoice(String paymentHash) {
        return invoicesPort.existsSettledInvoiceByPaymentHash(paymentHash);
    }
}
