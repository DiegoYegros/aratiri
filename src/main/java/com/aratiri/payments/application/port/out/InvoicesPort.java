package com.aratiri.payments.application.port.out;

import com.aratiri.payments.domain.DecodedInvoice;

public interface InvoicesPort {

    DecodedInvoice decodeInvoice(String paymentRequest);

    boolean existsSettledInvoice(String paymentHash);
}
