package com.aratiri.payments.application.command;

import com.aratiri.payments.application.dto.PayInvoiceRequestDTO;
import com.aratiri.payments.application.dto.PaymentResponseDTO;

import java.util.function.Supplier;

public interface LightningInvoicePaymentCommand {

    PaymentResponseDTO execute(
            String userId,
            String idempotencyKey,
            PayInvoiceRequestDTO request,
            Supplier<PaymentResponseDTO> execution
    );
}
