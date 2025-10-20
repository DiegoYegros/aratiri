package com.aratiri.payments.application.port.in;

import com.aratiri.payments.application.dto.OnChainPaymentDTOs;
import com.aratiri.payments.application.dto.PayInvoiceRequestDTO;
import com.aratiri.payments.application.dto.PaymentResponseDTO;
import lnrpc.Payment;

import java.util.Optional;

public interface PaymentsPort {

    PaymentResponseDTO payLightningInvoice(PayInvoiceRequestDTO request, String userId);

    Optional<Payment> checkPaymentStatusOnNode(String paymentHash);

    void initiateGrpcLightningPayment(String transactionId, String userId, PayInvoiceRequestDTO payRequest);

    void initiateGrpcOnChainPayment(String transactionId, String userId, OnChainPaymentDTOs.SendOnChainRequestDTO payRequest);

    OnChainPaymentDTOs.SendOnChainResponseDTO sendOnChain(OnChainPaymentDTOs.SendOnChainRequestDTO request, String userId);

    OnChainPaymentDTOs.EstimateFeeResponseDTO estimateOnChainFee(OnChainPaymentDTOs.EstimateFeeRequestDTO request, String userId);
}
