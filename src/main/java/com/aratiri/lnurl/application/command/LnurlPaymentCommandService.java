package com.aratiri.lnurl.application.command;

import com.aratiri.lnurl.application.dto.LnurlPayRequestDTO;
import com.aratiri.payments.application.PaymentCommandService;
import com.aratiri.payments.application.command.PaymentCommandFailurePayload;
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
public class LnurlPaymentCommandService {

    private static final String COMMAND_TYPE = "LNURL_PAY";

    private final PaymentCommandService paymentCommandService;

    @Transactional
    public PaymentResponseDTO execute(
            String userId,
            String idempotencyKey,
            LnurlPayRequestDTO request,
            Supplier<PaymentResponseDTO> execution
    ) {
        String canonicalPayload = JsonUtils.toJson(request);
        PaymentCommandService.PaymentCommandResult result = paymentCommandService.resolveIdempotency(
                userId, idempotencyKey, COMMAND_TYPE, canonicalPayload
        );

        return switch (result.type()) {
            case REPLAY -> JsonUtils.fromJson(result.responsePayload(), PaymentResponseDTO.class);
            case FAILED_REPLAY -> throw JsonUtils.fromJson(
                    result.responsePayload(), PaymentCommandFailurePayload.class
            ).toException();
            case IN_PROGRESS -> throw new AratiriException(
                    "LNURL payment with this idempotency key is still in progress",
                    HttpStatus.CONFLICT.value()
            );
            case NEW_COMMAND -> {
                try {
                    PaymentResponseDTO response = execution.get();
                    paymentCommandService.completeCommand(
                            result.commandId(), response.getTransactionId(), JsonUtils.toJson(response)
                    );
                    yield response;
                } catch (RuntimeException exception) {
                    paymentCommandService.failCommand(
                            result.commandId(), JsonUtils.toJson(PaymentCommandFailurePayload.from(exception))
                    );
                    throw exception;
                }
            }
        };
    }
}
