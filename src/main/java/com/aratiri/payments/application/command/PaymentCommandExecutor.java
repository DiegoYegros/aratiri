package com.aratiri.payments.application.command;

import com.aratiri.payments.application.PaymentCommandService;
import com.aratiri.payments.infrastructure.json.JsonUtils;
import com.aratiri.shared.exception.AratiriException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class PaymentCommandExecutor {

    private final PaymentCommandService paymentCommandService;

    @Transactional
    public <R> R execute(PaymentCommandExecution<R> command) {
        String canonicalPayload = JsonUtils.toJson(command.requestPayload());
        PaymentCommandService.PaymentCommandResult result = paymentCommandService.resolveIdempotency(
                command.userId(), command.idempotencyKey(), command.commandType(), canonicalPayload
        );

        return switch (result.type()) {
            case REPLAY -> JsonUtils.fromJson(result.responsePayload(), command.responseType());
            case FAILED_REPLAY -> throw JsonUtils.fromJson(
                    result.responsePayload(), PaymentCommandFailurePayload.class
            ).toException();
            case IN_PROGRESS -> throw new AratiriException(
                    command.inProgressMessage(),
                    HttpStatus.CONFLICT.value()
            );
            case NEW_COMMAND -> executeNewCommand(command, result);
        };
    }

    private <R> R executeNewCommand(
            PaymentCommandExecution<R> command,
            PaymentCommandService.PaymentCommandResult result
    ) {
        try {
            R response = command.execution().get();
            paymentCommandService.completeCommand(
                    result.commandId(),
                    command.transactionIdExtractor().apply(response),
                    JsonUtils.toJson(response)
            );
            return response;
        } catch (RuntimeException exception) {
            paymentCommandService.failCommand(
                    result.commandId(),
                    JsonUtils.toJson(PaymentCommandFailurePayload.from(exception))
            );
            throw exception;
        }
    }

    public record PaymentCommandExecution<R>(
            String userId,
            String idempotencyKey,
            String commandType,
            Object requestPayload,
            Supplier<R> execution,
            Class<R> responseType,
            Function<R, String> transactionIdExtractor,
            String inProgressMessage
    ) {
        public PaymentCommandExecution {
            Objects.requireNonNull(userId, "userId is required");
            Objects.requireNonNull(idempotencyKey, "idempotencyKey is required");
            Objects.requireNonNull(commandType, "commandType is required");
            Objects.requireNonNull(execution, "execution is required");
            Objects.requireNonNull(responseType, "responseType is required");
            Objects.requireNonNull(transactionIdExtractor, "transactionIdExtractor is required");
            Objects.requireNonNull(inProgressMessage, "inProgressMessage is required");
        }
    }
}
