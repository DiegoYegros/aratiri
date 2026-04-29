package com.aratiri.lnurl.application.command;

import com.aratiri.lnurl.application.dto.LnurlPayRequestDTO;
import com.aratiri.payments.application.command.PaymentCommandExecutor;
import com.aratiri.payments.application.dto.PaymentResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class LnurlPaymentCommandService {

    private static final String COMMAND_TYPE = "LNURL_PAY";
    private static final String IN_PROGRESS_MESSAGE = "LNURL payment with this idempotency key is still in progress";

    private final PaymentCommandExecutor paymentCommandExecutor;

    public PaymentResponseDTO execute(
            String userId,
            String idempotencyKey,
            LnurlPayRequestDTO request,
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
