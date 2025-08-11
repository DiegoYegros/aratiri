package com.aratiri.aratiri.service;

import com.aratiri.aratiri.dto.payments.OnChainPaymentDTOs;
import com.aratiri.aratiri.dto.payments.PayInvoiceRequestDTO;
import com.aratiri.aratiri.dto.payments.PaymentResponseDTO;
import lnrpc.Payment;

import java.util.Optional;

public interface PaymentService {
    PaymentResponseDTO payLightningInvoice(PayInvoiceRequestDTO request, String userId);

    Optional<Payment> checkPaymentStatusOnNode(String paymentHash);

    void initiateGrpcLightningPayment(String transactionId, String userId, PayInvoiceRequestDTO payRequest);

    void initiateGrpcOnChainPayment(String transactionId, String userId, OnChainPaymentDTOs.SendOnChainRequestDTO payRequest);

    OnChainPaymentDTOs.SendOnChainResponseDTO sendOnChain(OnChainPaymentDTOs.SendOnChainRequestDTO request, String userId);

}
