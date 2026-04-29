package com.aratiri.payments.application;

import com.aratiri.infrastructure.messaging.outbox.OutboxWriter;
import com.aratiri.payments.application.command.LightningInvoicePaymentCommand;
import com.aratiri.payments.application.command.OnChainPaymentCommand;
import com.aratiri.payments.application.dto.PayInvoiceRequestDTO;
import com.aratiri.payments.application.dto.PaymentResponseDTO;
import com.aratiri.payments.application.event.PaymentInitiatedEvent;
import com.aratiri.payments.application.port.out.*;
import com.aratiri.payments.domain.DecodedInvoice;
import com.aratiri.transactions.application.dto.CreateTransactionRequest;
import com.aratiri.transactions.application.dto.TransactionDTOResponse;
import com.aratiri.transactions.application.dto.TransactionStatus;
import com.aratiri.transactions.application.dto.TransactionType;
import com.aratiri.webhooks.application.WebhookEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentsAdapterTest {

    @Mock
    private AccountsPort accountsPort;

    @Mock
    private TransactionsPort transactionsPort;

    @Mock
    private InvoicesPort invoicesPort;

    @Mock
    private LightningNodePort lightningNodePort;

    @Mock
    private OutboxEventPort outboxEventPort;

    @Mock
    private OutboxWriter outboxWriter;

    @Mock
    private LightningInvoicePort lightningInvoicePort;

    @Mock
    private LightningInvoicePaymentCommand lightningInvoicePaymentCommand;

    @Mock
    private OnChainPaymentCommand onChainPaymentCommand;

    @Mock
    private WebhookEventService webhookEventService;

    private PaymentsAdapter paymentsAdapter;

    @BeforeEach
    void setUp() {
        paymentsAdapter = new PaymentsAdapter(
                accountsPort,
                transactionsPort,
                invoicesPort,
                lightningNodePort,
                outboxEventPort,
                outboxWriter,
                lightningInvoicePort,
                lightningInvoicePaymentCommand,
                onChainPaymentCommand,
                webhookEventService
        );
    }

    @Test
    void payLightningInvoice_externalPaymentDelegatesOutboxDetailsToWriter() {
        PayInvoiceRequestDTO request = new PayInvoiceRequestDTO();
        request.setInvoice("lnbc1external");
        request.setExternalReference("external-ref");
        request.setMetadata("{\"orderId\":\"order-123\"}");

        when(lightningInvoicePaymentCommand.execute(anyString(), anyString(), same(request), any()))
                .thenAnswer(invocation -> invocation.<java.util.function.Supplier<PaymentResponseDTO>>getArgument(3).get());
        when(invoicesPort.decodeInvoice("lnbc1external"))
                .thenReturn(new DecodedInvoice("payment-hash-123", 2_000L, "test invoice"));
        when(lightningNodePort.findPayment("payment-hash-123")).thenReturn(Optional.empty());
        when(lightningInvoicePort.findByPaymentHash("payment-hash-123")).thenReturn(Optional.empty());
        when(invoicesPort.existsSettledInvoice("payment-hash-123")).thenReturn(false);
        when(transactionsPort.createTransaction(any(CreateTransactionRequest.class))).thenReturn(
                TransactionDTOResponse.builder()
                        .id("tx-123")
                        .status(TransactionStatus.PENDING)
                        .type(TransactionType.LIGHTNING_DEBIT)
                        .build()
        );

        PaymentResponseDTO response = paymentsAdapter.payLightningInvoice(request, "user-123", "idem-key");

        assertEquals("tx-123", response.getTransactionId());
        ArgumentCaptor<PaymentInitiatedEvent> eventCaptor = ArgumentCaptor.forClass(PaymentInitiatedEvent.class);
        verify(outboxWriter).publishPaymentInitiated(eq("tx-123"), eventCaptor.capture());
        PaymentInitiatedEvent event = eventCaptor.getValue();
        assertEquals("user-123", event.getUserId());
        assertEquals("tx-123", event.getTransactionId());
        assertEquals(request, event.getPayRequest());
        verify(outboxEventPort, never()).save(any());
        verify(webhookEventService).createPaymentAcceptedEvent(argThat(facts ->
                facts.transactionId().equals("tx-123")
                        && facts.userId().equals("user-123")
                        && facts.type() == TransactionType.LIGHTNING_DEBIT
                        && facts.amountSat() == 2_000L
                        && facts.referenceId().equals("payment-hash-123")
        ));
        verifyNoMoreInteractions(outboxEventPort);
    }
}
