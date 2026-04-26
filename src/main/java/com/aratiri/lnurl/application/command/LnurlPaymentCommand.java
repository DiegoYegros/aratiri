package com.aratiri.lnurl.application.command;

import com.aratiri.lnurl.application.dto.LnurlPayRequestDTO;
import com.aratiri.payments.application.dto.PaymentResponseDTO;

import java.util.function.Supplier;

public interface LnurlPaymentCommand {

    PaymentResponseDTO execute(
            String userId,
            String idempotencyKey,
            LnurlPayRequestDTO request,
            Supplier<PaymentResponseDTO> execution
    );
}
