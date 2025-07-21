package com.aratiri.aratiri.service;

import com.aratiri.aratiri.dto.payments.PayInvoiceRequestDTO;
import com.aratiri.aratiri.dto.payments.PaymentResponseDTO;
import lnrpc.Payment;

import java.util.Optional;

public interface PaymentService {
    PaymentResponseDTO payLightningInvoice(PayInvoiceRequestDTO request, String userId);

    Optional<Payment> checkPaymentStatusOnNode(String paymentHash);

    void initiateGrpcPayment(String transactionId, String userId, PayInvoiceRequestDTO payRequest);
}
