package com.aratiri.payments.application.command;

import com.aratiri.payments.application.PaymentCommandService;
import com.aratiri.payments.application.dto.OnChainPaymentDTOs;
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
class OnChainPaymentCommandServiceTest {

    @Mock
    private PaymentCommandService paymentCommandService;

    private OnChainPaymentCommandService commandService;

    @BeforeEach
    void setUp() {
        commandService = new OnChainPaymentCommandService(paymentCommandService);
    }

    @Test
    void execute_newCommand_executesAndCompletes() {
        UUID commandId = UUID.randomUUID();
        OnChainPaymentDTOs.SendOnChainRequestDTO request = request("bc1ptest", 5_000L);
        OnChainPaymentDTOs.SendOnChainResponseDTO expected = response("tx-123");

        when(paymentCommandService.resolveIdempotency(any(), any(), eq("ONCHAIN_SEND"), any()))
                .thenReturn(PaymentCommandService.PaymentCommandResult.newCommand(commandId));

        OnChainPaymentDTOs.SendOnChainResponseDTO result = commandService.execute(
                "user-1", "key-1", request, () -> expected
        );

        assertEquals(expected, result);
        verify(paymentCommandService).completeCommand(commandId, "tx-123", JsonUtils.toJson(expected));
    }

    @Test
    void execute_replay_returnsCachedResponseWithoutExecuting() {
        OnChainPaymentDTOs.SendOnChainRequestDTO request = request("bc1ptest", 5_000L);
        OnChainPaymentDTOs.SendOnChainResponseDTO cached = response("tx-123");

        when(paymentCommandService.resolveIdempotency(any(), any(), eq("ONCHAIN_SEND"), any()))
                .thenReturn(PaymentCommandService.PaymentCommandResult.replay("tx-123", JsonUtils.toJson(cached)));

        OnChainPaymentDTOs.SendOnChainResponseDTO result = commandService.execute("user-1", "key-1", request, () -> {
            throw new AssertionError("Execution should not be called on replay");
        });

        assertEquals("tx-123", result.getTransactionId());
        assertEquals(TransactionStatus.PENDING, result.getTransactionStatus());
        verify(paymentCommandService, never()).completeCommand(any(), any(), any());
    }

    @Test
    void execute_inProgress_throwsConflict() {
        OnChainPaymentDTOs.SendOnChainRequestDTO request = request("bc1ptest", 5_000L);

        when(paymentCommandService.resolveIdempotency(any(), any(), eq("ONCHAIN_SEND"), any()))
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
        OnChainPaymentDTOs.SendOnChainRequestDTO request = request("bc1ptest", 5_000L);

        when(paymentCommandService.resolveIdempotency(any(), any(), eq("ONCHAIN_SEND"), any()))
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
        OnChainPaymentDTOs.SendOnChainRequestDTO request = request("bc1ptest", 5_000L);
        PaymentCommandFailurePayload failure = new PaymentCommandFailurePayload(
                "Payment to self not allowed.",
                HttpStatus.BAD_REQUEST.value()
        );

        when(paymentCommandService.resolveIdempotency(any(), any(), eq("ONCHAIN_SEND"), any()))
                .thenReturn(PaymentCommandService.PaymentCommandResult.failedReplay(JsonUtils.toJson(failure)));

        AratiriException exception = assertThrows(AratiriException.class, () ->
                commandService.execute("user-1", "key-1", request, () -> {
                    throw new AssertionError("Execution should not be called on failed replay");
                })
        );

        assertEquals("Payment to self not allowed.", exception.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST.value(), exception.getStatus());
        verify(paymentCommandService, never()).completeCommand(any(), any(), any());
    }

    @Test
    void execute_executionFailure_recordsFailureAndPropagates() {
        UUID commandId = UUID.randomUUID();
        OnChainPaymentDTOs.SendOnChainRequestDTO request = request("bc1ptest", 5_000L);
        AratiriException failure = new AratiriException("Payment failed", HttpStatus.BAD_REQUEST.value());

        when(paymentCommandService.resolveIdempotency(any(), any(), eq("ONCHAIN_SEND"), any()))
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

    private OnChainPaymentDTOs.SendOnChainRequestDTO request(String address, Long amountSat) {
        OnChainPaymentDTOs.SendOnChainRequestDTO request = new OnChainPaymentDTOs.SendOnChainRequestDTO();
        request.setAddress(address);
        request.setSatsAmount(amountSat);
        request.setTargetConf(6);
        return request;
    }

    private OnChainPaymentDTOs.SendOnChainResponseDTO response(String transactionId) {
        OnChainPaymentDTOs.SendOnChainResponseDTO response = new OnChainPaymentDTOs.SendOnChainResponseDTO();
        response.setTransactionId(transactionId);
        response.setTransactionStatus(TransactionStatus.PENDING);
        return response;
    }
}
