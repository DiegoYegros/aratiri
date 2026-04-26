package com.aratiri.lnurl.application.command;

import com.aratiri.lnurl.application.dto.LnurlPayRequestDTO;
import com.aratiri.payments.application.PaymentCommandService;
import com.aratiri.payments.application.command.PaymentCommandFailurePayload;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LnurlPaymentCommandServiceTest {

    @Mock
    private PaymentCommandService paymentCommandService;

    private LnurlPaymentCommandService commandService;

    @BeforeEach
    void setUp() {
        commandService = new LnurlPaymentCommandService(paymentCommandService);
    }

    @Test
    void execute_newCommand_executesAndCompletes() {
        UUID commandId = UUID.randomUUID();
        LnurlPayRequestDTO request = request("https://example.test/lnurl", 10_000L);
        PaymentResponseDTO expected = response("tx-123");

        when(paymentCommandService.resolveIdempotency(any(), any(), eq("LNURL_PAY"), any()))
                .thenReturn(PaymentCommandService.PaymentCommandResult.newCommand(commandId));

        PaymentResponseDTO result = commandService.execute("user-1", "key-1", request, () -> expected);

        assertEquals(expected, result);
        verify(paymentCommandService).completeCommand(commandId, "tx-123", JsonUtils.toJson(expected));
    }

    @Test
    void execute_replay_returnsCachedResponseWithoutExecuting() {
        LnurlPayRequestDTO request = request("https://example.test/lnurl", 10_000L);
        PaymentResponseDTO cached = response("tx-123");

        when(paymentCommandService.resolveIdempotency(any(), any(), eq("LNURL_PAY"), any()))
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
        LnurlPayRequestDTO request = request("https://example.test/lnurl", 10_000L);

        when(paymentCommandService.resolveIdempotency(any(), any(), eq("LNURL_PAY"), any()))
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
        LnurlPayRequestDTO request = request("https://example.test/lnurl", 10_000L);

        when(paymentCommandService.resolveIdempotency(any(), any(), eq("LNURL_PAY"), any()))
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
        LnurlPayRequestDTO request = request("https://example.test/lnurl", 10_000L);
        PaymentCommandFailurePayload failure = new PaymentCommandFailurePayload(
                "Failed to fetch invoice from LNURL callback.",
                HttpStatus.BAD_GATEWAY.value()
        );

        when(paymentCommandService.resolveIdempotency(any(), any(), eq("LNURL_PAY"), any()))
                .thenReturn(PaymentCommandService.PaymentCommandResult.failedReplay(JsonUtils.toJson(failure)));

        AratiriException exception = assertThrows(AratiriException.class, () ->
                commandService.execute("user-1", "key-1", request, () -> {
                    throw new AssertionError("Execution should not be called on failed replay");
                })
        );

        assertEquals("Failed to fetch invoice from LNURL callback.", exception.getMessage());
        assertEquals(HttpStatus.BAD_GATEWAY.value(), exception.getStatus());
        verify(paymentCommandService, never()).completeCommand(any(), any(), any());
    }

    @Test
    void execute_executionFailure_recordsFailureAndPropagates() {
        UUID commandId = UUID.randomUUID();
        LnurlPayRequestDTO request = request("https://example.test/lnurl", 10_000L);
        AratiriException failure = new AratiriException("Payment failed", HttpStatus.BAD_REQUEST.value());

        when(paymentCommandService.resolveIdempotency(any(), any(), eq("LNURL_PAY"), any()))
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

    private LnurlPayRequestDTO request(String callback, Long amountMsat) {
        LnurlPayRequestDTO request = new LnurlPayRequestDTO();
        request.setCallback(callback);
        request.setAmountMsat(amountMsat);
        request.setComment("memo");
        return request;
    }

    private PaymentResponseDTO response(String transactionId) {
        return PaymentResponseDTO.builder()
                .transactionId(transactionId)
                .status(TransactionStatus.PENDING)
                .message("Payment initiated. Status is pending.")
                .build();
    }
}
