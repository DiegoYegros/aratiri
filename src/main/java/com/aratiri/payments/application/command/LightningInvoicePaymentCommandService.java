package com.aratiri.payments.application.command;

import com.aratiri.payments.application.dto.PayInvoiceRequestDTO;
import com.aratiri.payments.application.dto.PaymentResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class LightningInvoicePaymentCommandService {

    private static final String COMMAND_TYPE = "LIGHTNING_INVOICE_PAY";
    private static final String IN_PROGRESS_MESSAGE = "Payment with this idempotency key is still in progress";

    private final PaymentCommandExecutor paymentCommandExecutor;

    public PaymentResponseDTO execute(
            String userId,
            String idempotencyKey,
            PayInvoiceRequestDTO request,
            Supplier<PaymentResponseDTO> execution
    ) {
        return paymentCommandExecutor.execute(new PaymentCommandExecutor.PaymentCommandExecution<>(
                userId,
                idempotencyKey,
                COMMAND_TYPE,
                request,
                execution,
                PaymentResponseDTO.class,
                PaymentResponseDTO::getTransactionId,
                IN_PROGRESS_MESSAGE
        ));
    }
}
