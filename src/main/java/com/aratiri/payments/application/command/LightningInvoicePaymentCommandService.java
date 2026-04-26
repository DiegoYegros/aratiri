package com.aratiri.payments.application.command;

import com.aratiri.payments.application.PaymentCommandService;
import com.aratiri.payments.application.dto.PayInvoiceRequestDTO;
import com.aratiri.payments.application.dto.PaymentResponseDTO;
import com.aratiri.payments.infrastructure.json.JsonUtils;
import com.aratiri.shared.exception.AratiriException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class LightningInvoicePaymentCommandService implements LightningInvoicePaymentCommand {

    private static final String COMMAND_TYPE = "LIGHTNING_INVOICE_PAY";

    private final PaymentCommandService paymentCommandService;

    @Override
    @Transactional
    public PaymentResponseDTO execute(
            String userId,
            String idempotencyKey,
            PayInvoiceRequestDTO request,
            Supplier<PaymentResponseDTO> execution
    ) {
        String canonicalPayload = JsonUtils.toJson(request);
        PaymentCommandService.PaymentCommandResult result = paymentCommandService.resolveIdempotency(
                userId, idempotencyKey, COMMAND_TYPE, canonicalPayload
        );

        return switch (result.type()) {
            case REPLAY -> JsonUtils.fromJson(result.responsePayload(), PaymentResponseDTO.class);
            case IN_PROGRESS -> throw new AratiriException(
                    "Payment with this idempotency key is still in progress",
                    HttpStatus.CONFLICT.value()
            );
            case NEW_COMMAND -> {
                PaymentResponseDTO response = execution.get();
                paymentCommandService.completeCommand(
                        result.commandId(), response.getTransactionId(), JsonUtils.toJson(response)
                );
                yield response;
            }
        };
    }
}