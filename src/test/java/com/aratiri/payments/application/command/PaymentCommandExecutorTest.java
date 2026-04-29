package com.aratiri.payments.application.command;

import com.aratiri.payments.application.PaymentCommandService;
import com.aratiri.payments.application.dto.PayInvoiceRequestDTO;
import com.aratiri.payments.application.dto.PaymentResponseDTO;
import com.aratiri.payments.infrastructure.json.JsonUtils;
import com.aratiri.shared.exception.AratiriException;
import com.aratiri.transactions.application.dto.TransactionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentCommandExecutorTest {

    @Mock
    private PaymentCommandService paymentCommandService;

    private PaymentCommandExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new PaymentCommandExecutor(paymentCommandService);
    }

    @Test
    void execute_newCommand_executesAndCompletes() {
        UUID commandId = UUID.randomUUID();
        PayInvoiceRequestDTO request = request("lnbc1test");
        PaymentResponseDTO expected = response("tx-123");

        when(paymentCommandService.resolveIdempotency(
                "user-1", "key-1", "LIGHTNING_INVOICE_PAY", JsonUtils.toJson(request)
        )).thenReturn(PaymentCommandService.PaymentCommandResult.newCommand(commandId));

        PaymentResponseDTO result = executor.execute(command(request, () -> expected));

        assertSame(expected, result);
        verify(paymentCommandService).completeCommand(commandId, "tx-123", JsonUtils.toJson(expected));
    }

    @Test
    void execute_replay_returnsCachedResponseWithoutExecuting() {
        PayInvoiceRequestDTO request = request("lnbc1test");
        PaymentResponseDTO cached = response("tx-123");

        when(paymentCommandService.resolveIdempotency(any(), any(), eq("LIGHTNING_INVOICE_PAY"), any()))
                .thenReturn(PaymentCommandService.PaymentCommandResult.replay("tx-123", JsonUtils.toJson(cached)));

        PaymentResponseDTO result = executor.execute(command(request, () -> {
            throw new AssertionError("Execution should not be called on replay");
        }));

        assertEquals("tx-123", result.getTransactionId());
        assertEquals(TransactionStatus.PENDING, result.getStatus());
        verify(paymentCommandService, never()).completeCommand(any(), any(), any());
    }

    @Test
    void execute_inProgress_throwsConflictWithoutExecuting() {
        PayInvoiceRequestDTO request = request("lnbc1test");

        when(paymentCommandService.resolveIdempotency(any(), any(), eq("LIGHTNING_INVOICE_PAY"), any()))
                .thenReturn(PaymentCommandService.PaymentCommandResult.inProgress("tx-123"));

        var command = command(request, () -> {
            throw new AssertionError("Execution should not be called when in progress");
        });
        AratiriException exception = assertThrows(AratiriException.class, () ->
                executor.execute(command));

        assertEquals("Payment with this idempotency key is still in progress", exception.getMessage());
        assertEquals(HttpStatus.CONFLICT.value(), exception.getStatus());
        verify(paymentCommandService, never()).completeCommand(any(), any(), any());
    }

    @Test
    void execute_conflictPropagatesExceptionWithoutExecuting() {
        PayInvoiceRequestDTO request = request("lnbc1test");

        when(paymentCommandService.resolveIdempotency(any(), any(), eq("LIGHTNING_INVOICE_PAY"), any()))
                .thenThrow(new AratiriException(
                        "Idempotency key conflict: different request payload for the same key",
                        HttpStatus.CONFLICT.value()
                ));

        var command = command(request, () -> {
            throw new AssertionError("Execution should not be called on conflict");
        });
        AratiriException exception = assertThrows(AratiriException.class, () ->
                executor.execute(command));

        assertEquals(HttpStatus.CONFLICT.value(), exception.getStatus());
        verify(paymentCommandService, never()).completeCommand(any(), any(), any());
    }

    @Test
    void execute_failedReplay_throwsStoredFailureWithoutExecuting() {
        PayInvoiceRequestDTO request = request("lnbc1test");
        PaymentCommandFailurePayload failure = new PaymentCommandFailurePayload(
                "Invoice has already been paid",
                HttpStatus.BAD_REQUEST.value()
        );

        when(paymentCommandService.resolveIdempotency(any(), any(), eq("LIGHTNING_INVOICE_PAY"), any()))
                .thenReturn(PaymentCommandService.PaymentCommandResult.failedReplay(JsonUtils.toJson(failure)));

        var command = command(request, () -> {
            throw new AssertionError("Execution should not be called on failed replay");
        });
        AratiriException exception = assertThrows(AratiriException.class, () ->
                executor.execute(command));

        assertEquals("Invoice has already been paid", exception.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST.value(), exception.getStatus());
        verify(paymentCommandService, never()).completeCommand(any(), any(), any());
    }

    @Test
    void execute_executionFailure_recordsFailureAndPropagates() {
        UUID commandId = UUID.randomUUID();
        PayInvoiceRequestDTO request = request("lnbc1test");
        AratiriException failure = new AratiriException("Payment failed", HttpStatus.BAD_REQUEST.value());

        when(paymentCommandService.resolveIdempotency(any(), any(), eq("LIGHTNING_INVOICE_PAY"), any()))
                .thenReturn(PaymentCommandService.PaymentCommandResult.newCommand(commandId));

        var command = command(request, () -> {
            throw failure;
        });
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                executor.execute(command));

        assertSame(failure, exception);
        verify(paymentCommandService, never()).completeCommand(any(), any(), any());
        verify(paymentCommandService).failCommand(commandId, JsonUtils.toJson(PaymentCommandFailurePayload.from(failure)));
    }

    private PaymentCommandExecutor.PaymentCommandExecution<PaymentResponseDTO> command(
            PayInvoiceRequestDTO request,
            java.util.function.Supplier<PaymentResponseDTO> execution
    ) {
        return new PaymentCommandExecutor.PaymentCommandExecution<>(
                "user-1",
                "key-1",
                "LIGHTNING_INVOICE_PAY",
                request,
                execution,
                PaymentResponseDTO.class,
                PaymentResponseDTO::getTransactionId,
                "Payment with this idempotency key is still in progress"
        );
    }

    private PayInvoiceRequestDTO request(String invoice) {
        PayInvoiceRequestDTO request = new PayInvoiceRequestDTO();
        request.setInvoice(invoice);
        return request;
    }

    private PaymentResponseDTO response(String transactionId) {
        return PaymentResponseDTO.builder()
                .transactionId(transactionId)
                .status(TransactionStatus.PENDING)
                .message("Done")
                .build();
    }
}
