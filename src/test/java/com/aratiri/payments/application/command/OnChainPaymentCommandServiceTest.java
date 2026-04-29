package com.aratiri.payments.application.command;

import com.aratiri.payments.application.dto.OnChainPaymentDTOs;
import com.aratiri.transactions.application.dto.TransactionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OnChainPaymentCommandServiceTest {

    @Mock
    private PaymentCommandExecutor paymentCommandExecutor;

    private OnChainPaymentCommandService commandService;

    @BeforeEach
    void setUp() {
        commandService = new OnChainPaymentCommandService(paymentCommandExecutor);
    }

    @Test
    void execute_delegatesWithOnChainCommandDefinition() {
        OnChainPaymentDTOs.SendOnChainRequestDTO request = new OnChainPaymentDTOs.SendOnChainRequestDTO();
        request.setAddress("bc1ptest");
        request.setSatsAmount(5_000L);
        OnChainPaymentDTOs.SendOnChainResponseDTO expected = new OnChainPaymentDTOs.SendOnChainResponseDTO();
        expected.setTransactionId("tx-123");
        expected.setTransactionStatus(TransactionStatus.PENDING);

        when(paymentCommandExecutor.execute(any())).thenReturn(expected);

        OnChainPaymentDTOs.SendOnChainResponseDTO result = commandService.execute(
                "user-1", "key-1", request, () -> expected
        );

        assertSame(expected, result);
        ArgumentCaptor<PaymentCommandExecutor.PaymentCommandExecution<OnChainPaymentDTOs.SendOnChainResponseDTO>> captor =
                ArgumentCaptor.captor();
        verify(paymentCommandExecutor).execute(captor.capture());
        PaymentCommandExecutor.PaymentCommandExecution<OnChainPaymentDTOs.SendOnChainResponseDTO> command =
                captor.getValue();
        assertEquals("user-1", command.userId());
        assertEquals("key-1", command.idempotencyKey());
        assertEquals("ONCHAIN_SEND", command.commandType());
        assertSame(request, command.requestPayload());
        assertSame(expected, command.execution().get());
        assertEquals(OnChainPaymentDTOs.SendOnChainResponseDTO.class, command.responseType());
        assertEquals("tx-123", command.transactionIdExtractor().apply(expected));
        assertEquals("Payment with this idempotency key is still in progress", command.inProgressMessage());
    }
}
