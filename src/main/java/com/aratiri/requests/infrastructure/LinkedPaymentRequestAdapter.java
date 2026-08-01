package com.aratiri.requests.infrastructure;

import com.aratiri.infrastructure.persistence.jpa.repository.PaymentRequestRepository;
import com.aratiri.invoices.application.port.out.LinkedPaymentRequestPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class LinkedPaymentRequestAdapter implements LinkedPaymentRequestPort {

    private final PaymentRequestRepository paymentRequestRepository;

    public LinkedPaymentRequestAdapter(PaymentRequestRepository paymentRequestRepository) {
        this.paymentRequestRepository = paymentRequestRepository;
    }

    @Override
    @Transactional
    public void markPaidByPaymentHash(String paymentHash, Instant paidAt) {
        paymentRequestRepository.markPaidByPaymentHash(paymentHash, paidAt);
    }
}
