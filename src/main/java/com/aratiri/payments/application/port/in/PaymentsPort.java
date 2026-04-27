package com.aratiri.payments.application.port.in;

import com.aratiri.payments.application.dto.OnChainPaymentDTOs;
import com.aratiri.payments.application.dto.PayInvoiceRequestDTO;
import com.aratiri.payments.application.dto.PaymentResponseDTO;
import lnrpc.Payment;

import java.util.Optional;

public interface PaymentsPort {

    PaymentResponseDTO payLightningInvoice(PayInvoiceRequestDTO request, String userId, String idempotencyKey);

    PaymentResponseDTO payLightningInvoiceInternal(PayInvoiceRequestDTO request, String userId);

    Optional<Payment> checkPaymentStatusOnNode(String paymentHash);

    OnChainPaymentDTOs.SendOnChainResponseDTO sendOnChain(OnChainPaymentDTOs.SendOnChainRequestDTO request, String userId, String idempotencyKey);

    OnChainPaymentDTOs.EstimateFeeResponseDTO estimateOnChainFee(OnChainPaymentDTOs.EstimateFeeRequestDTO request, String userId);
}
