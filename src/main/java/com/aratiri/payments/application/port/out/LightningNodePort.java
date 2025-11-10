package com.aratiri.payments.application.port.out;

import com.aratiri.payments.application.dto.OnChainPaymentDTOs;
import com.aratiri.payments.application.dto.PayInvoiceRequestDTO;
import com.aratiri.payments.domain.OnChainFeeEstimate;
import lnrpc.Payment;

import java.util.Optional;

public interface LightningNodePort {

    Payment executeLightningPayment(PayInvoiceRequestDTO request, int defaultFeeLimitSat, int defaultTimeoutSeconds);

    Optional<Payment> findPayment(String paymentHash);

    String sendOnChain(OnChainPaymentDTOs.SendOnChainRequestDTO request);

    OnChainFeeEstimate estimateOnChainFee(OnChainPaymentDTOs.EstimateFeeRequestDTO request);
}
