package com.aratiri.payments.application.command;

import com.aratiri.payments.application.dto.OnChainPaymentDTOs;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class OnChainPaymentCommandService {

    private static final String COMMAND_TYPE = "ONCHAIN_SEND";
    private static final String IN_PROGRESS_MESSAGE = "Payment with this idempotency key is still in progress";

    private final PaymentCommandExecutor paymentCommandExecutor;

    public OnChainPaymentDTOs.SendOnChainResponseDTO execute(
            String userId,
            String idempotencyKey,
            OnChainPaymentDTOs.SendOnChainRequestDTO request,
            Supplier<OnChainPaymentDTOs.SendOnChainResponseDTO> execution
    ) {
        return paymentCommandExecutor.execute(new PaymentCommandExecutor.PaymentCommandExecution<>(
                userId,
                idempotencyKey,
                COMMAND_TYPE,
                request,
                execution,
                OnChainPaymentDTOs.SendOnChainResponseDTO.class,
                OnChainPaymentDTOs.SendOnChainResponseDTO::getTransactionId,
                IN_PROGRESS_MESSAGE
        ));
    }
}
