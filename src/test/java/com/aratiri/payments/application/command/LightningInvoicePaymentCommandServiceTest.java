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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LightningInvoicePaymentCommandServiceTest {

    @Mock
    private PaymentCommandService paymentCommandService;

    private LightningInvoicePaymentCommandService commandService;

    @BeforeEach
    void setUp() {
        commandService = new LightningInvoicePaymentCommandService(paymentCommandService);
    }

    @Test
    void execute_newCommand_executesAndCompletes() {
        UUID commandId = UUID.randomUUID();
        PayInvoiceRequestDTO request = new PayInvoiceRequestDTO();
        request.setInvoice("lnbc1test");
        PaymentResponseDTO expected = PaymentResponseDTO.builder()
                .transactionId("tx-123")
                .status(TransactionStatus.PENDING)
                .message("Done")
                .build();

        when(paymentCommandService.resolveIdempotency(any(), any(), eq("LIGHTNING_INVOICE_PAY"), any()))
                .thenReturn(PaymentCommandService.PaymentCommandResult.newCommand(commandId));

        PaymentResponseDTO result = commandService.execute("user-1", "key-1", request, () -> expected);

        assertEquals(expected, result);
        verify(paymentCommandService).completeCommand(commandId, "tx-123", JsonUtils.toJson(expected));
    }

    @Test
    void execute_replay_returnsCachedResponseWithoutExecuting() {
        PayInvoiceRequestDTO request = new PayInvoiceRequestDTO();
        request.setInvoice("lnbc1test");
        PaymentResponseDTO cached = PaymentResponseDTO.builder()
                .transactionId("tx-123")
                .status(TransactionStatus.PENDING)
                .message("Done")
                .build();

        when(paymentCommandService.resolveIdempotency(any(), any(), eq("LIGHTNING_INVOICE_PAY"), any()))
                .thenReturn(PaymentCommandService.PaymentCommandResult.replay("tx-123", JsonUtils.toJson(cached)));

        PaymentResponseDTO result = commandService.execute("user-1", "key-1", request, () -> {
            throw new AssertionError("Execution should not be called on replay");
        });

        assertEquals("tx-123", result.getTransactionId());
        assertEquals(TransactionStatus.PENDING, result.getStatus());
        verify(paymentCommandService, never()).completeCommand(any(), any(), any());
    }

    @Test
    void execute_inProgress_throwsConflict() {
        PayInvoiceRequestDTO request = new PayInvoiceRequestDTO();
        request.setInvoice("lnbc1test");

        when(paymentCommandService.resolveIdempotency(any(), any(), eq("LIGHTNING_INVOICE_PAY"), any()))
                .thenReturn(PaymentCommandService.PaymentCommandResult.inProgress("tx-123"));

        AratiriException exception = assertThrows(AratiriException.class, () ->
                commandService.execute("user-1", "key-1", request, () -> {
                    throw new AssertionError("Execution should not be called when in progress");
                })
        );

        assertEquals(HttpStatus.CONFLICT.value(), exception.getStatus());
        verify(paymentCommandService, never()).completeCommand(any(), any(), any());
    }

    @Test
    void execute_conflictPropagatesException() {
        PayInvoiceRequestDTO request = new PayInvoiceRequestDTO();
        request.setInvoice("lnbc1test");

        when(paymentCommandService.resolveIdempotency(any(), any(), eq("LIGHTNING_INVOICE_PAY"), any()))
                .thenThrow(new AratiriException(
                        "Idempotency key conflict: different request payload for the same key",
                        HttpStatus.CONFLICT.value()
                ));

        AratiriException exception = assertThrows(AratiriException.class, () ->
                commandService.execute("user-1", "key-1", request, () -> {
                    throw new AssertionError("Execution should not be called on conflict");
                })
        );

        assertEquals(HttpStatus.CONFLICT.value(), exception.getStatus());
        verify(paymentCommandService, never()).completeCommand(any(), any(), any());
    }

    @Test
    void execute_failedReplay_throwsStoredFailureWithoutExecuting() {
        PayInvoiceRequestDTO request = new PayInvoiceRequestDTO();
        request.setInvoice("lnbc1test");
        PaymentCommandFailurePayload failure = new PaymentCommandFailurePayload(
                "Invoice has already been paid",
                HttpStatus.BAD_REQUEST.value()
        );

        when(paymentCommandService.resolveIdempotency(any(), any(), eq("LIGHTNING_INVOICE_PAY"), any()))
                .thenReturn(PaymentCommandService.PaymentCommandResult.failedReplay(JsonUtils.toJson(failure)));

        AratiriException exception = assertThrows(AratiriException.class, () ->
                commandService.execute("user-1", "key-1", request, () -> {
                    throw new AssertionError("Execution should not be called on failed replay");
                })
        );

        assertEquals("Invoice has already been paid", exception.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST.value(), exception.getStatus());
        verify(paymentCommandService, never()).completeCommand(any(), any(), any());
    }

    @Test
    void execute_executionFailure_recordsFailureAndPropagates() {
        UUID commandId = UUID.randomUUID();
        PayInvoiceRequestDTO request = new PayInvoiceRequestDTO();
        request.setInvoice("lnbc1test");
        AratiriException failure = new AratiriException("Payment failed", HttpStatus.BAD_REQUEST.value());

        when(paymentCommandService.resolveIdempotency(any(), any(), eq("LIGHTNING_INVOICE_PAY"), any()))
                .thenReturn(PaymentCommandService.PaymentCommandResult.newCommand(commandId));

        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                commandService.execute("user-1", "key-1", request, () -> {
                    throw failure;
                })
        );

        assertEquals("Payment failed", exception.getMessage());
        verify(paymentCommandService, never()).completeCommand(any(), any(), any());
        verify(paymentCommandService).failCommand(eq(commandId), any());
    }
}
