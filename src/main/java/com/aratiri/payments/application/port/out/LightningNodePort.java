package com.aratiri.payments.application.port.out;

import com.aratiri.dto.payments.OnChainPaymentDTOs;
import com.aratiri.dto.payments.PayInvoiceRequestDTO;
import com.aratiri.payments.domain.OnChainFeeEstimate;
import lnrpc.Payment;

import java.util.Optional;

public interface LightningNodePort {

    Payment executeLightningPayment(PayInvoiceRequestDTO request, int defaultFeeLimitSat, int defaultTimeoutSeconds);

    Optional<Payment> findPayment(String paymentHash);

    String sendOnChain(OnChainPaymentDTOs.SendOnChainRequestDTO request);

    OnChainFeeEstimate estimateOnChainFee(OnChainPaymentDTOs.EstimateFeeRequestDTO request);
}
