package com.aratiri.payments.application.command;

import com.aratiri.payments.application.dto.PayInvoiceRequestDTO;
import com.aratiri.payments.application.dto.PaymentResponseDTO;
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
class LightningInvoicePaymentCommandServiceTest {

    @Mock
    private PaymentCommandExecutor paymentCommandExecutor;

    private LightningInvoicePaymentCommandService commandService;

    @BeforeEach
    void setUp() {
        commandService = new LightningInvoicePaymentCommandService(paymentCommandExecutor);
    }

    @Test
    void execute_delegatesWithLightningInvoiceCommandDefinition() {
        PayInvoiceRequestDTO request = new PayInvoiceRequestDTO();
        request.setInvoice("lnbc1test");
        PaymentResponseDTO expected = PaymentResponseDTO.builder()
                .transactionId("tx-123")
                .status(TransactionStatus.PENDING)
                .message("Done")
                .build();

        when(paymentCommandExecutor.execute(any())).thenReturn(expected);

        PaymentResponseDTO result = commandService.execute("user-1", "key-1", request, () -> expected);

        assertSame(expected, result);
        ArgumentCaptor<PaymentCommandExecutor.PaymentCommandExecution<PaymentResponseDTO>> captor =
                ArgumentCaptor.captor();
        verify(paymentCommandExecutor).execute(captor.capture());
        PaymentCommandExecutor.PaymentCommandExecution<PaymentResponseDTO> command = captor.getValue();
        assertEquals("user-1", command.userId());
        assertEquals("key-1", command.idempotencyKey());
        assertEquals("LIGHTNING_INVOICE_PAY", command.commandType());
        assertSame(request, command.requestPayload());
        assertSame(expected, command.execution().get());
        assertEquals(PaymentResponseDTO.class, command.responseType());
        assertEquals("tx-123", command.transactionIdExtractor().apply(expected));
        assertEquals("Payment with this idempotency key is still in progress", command.inProgressMessage());
    }
}
