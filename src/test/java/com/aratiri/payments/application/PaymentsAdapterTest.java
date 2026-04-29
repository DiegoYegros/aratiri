package com.aratiri.payments.application;

import com.aratiri.infrastructure.messaging.outbox.OutboxWriter;
import com.aratiri.payments.application.command.LightningInvoicePaymentCommand;
import com.aratiri.payments.application.command.OnChainPaymentCommand;
import com.aratiri.payments.application.dto.OnChainPaymentDTOs;
import com.aratiri.payments.application.dto.PayInvoiceRequestDTO;
import com.aratiri.payments.application.dto.PaymentResponseDTO;
import com.aratiri.payments.application.event.OnChainPaymentInitiatedEvent;
import com.aratiri.payments.application.event.PaymentInitiatedEvent;
import com.aratiri.payments.application.port.out.*;
import com.aratiri.payments.domain.DecodedInvoice;
import com.aratiri.payments.domain.InternalLightningInvoice;
import com.aratiri.payments.domain.OnChainFeeEstimate;
import com.aratiri.payments.domain.PaymentAccount;
import com.aratiri.transactions.application.dto.CreateTransactionRequest;
import com.aratiri.transactions.application.dto.TransactionDTOResponse;
import com.aratiri.transactions.application.dto.TransactionCurrency;
import com.aratiri.transactions.application.dto.TransactionStatus;
import com.aratiri.transactions.application.dto.TransactionType;
import com.aratiri.transactions.application.event.InternalTransferInitiatedEvent;
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
        verify(webhookEventService).createPaymentAcceptedEvent(argThat(facts ->
                facts.transactionId().equals("tx-123")
                        && facts.userId().equals("user-123")
                        && facts.type() == TransactionType.LIGHTNING_DEBIT
                        && facts.amountSat() == 2_000L
                        && facts.referenceId().equals("payment-hash-123")
        ));
    }

    @Test
    void sendOnChain_delegatesOutboxDetailsToWriter() {
        OnChainPaymentDTOs.SendOnChainRequestDTO request = new OnChainPaymentDTOs.SendOnChainRequestDTO();
        request.setAddress("bitcoin:bc1qrecipient");
        request.setSatsAmount(3_000L);
        request.setTargetConf(3);
        request.setExternalReference("external-ref");
        request.setMetadata("{\"orderId\":\"order-456\"}");

        when(onChainPaymentCommand.execute(anyString(), anyString(), same(request), any()))
                .thenAnswer(invocation -> invocation.<java.util.function.Supplier<OnChainPaymentDTOs.SendOnChainResponseDTO>>getArgument(3).get());
        when(accountsPort.getAccount("user-123"))
                .thenReturn(new PaymentAccount("user-123", 10_000L, "bc1qsender"));
        when(lightningNodePort.estimateOnChainFee(any(OnChainPaymentDTOs.EstimateFeeRequestDTO.class)))
                .thenReturn(new OnChainFeeEstimate(250L, 12L));
        when(transactionsPort.createTransaction(any(CreateTransactionRequest.class))).thenReturn(
                TransactionDTOResponse.builder()
                        .id("tx-onchain")
                        .status(TransactionStatus.PENDING)
                        .type(TransactionType.ONCHAIN_DEBIT)
                        .currency(TransactionCurrency.BTC)
                        .build()
        );

        OnChainPaymentDTOs.SendOnChainResponseDTO response = paymentsAdapter.sendOnChain(request, "user-123", "idem-key");

        assertEquals("tx-onchain", response.getTransactionId());
        ArgumentCaptor<OnChainPaymentInitiatedEvent> eventCaptor = ArgumentCaptor.forClass(OnChainPaymentInitiatedEvent.class);
        verify(outboxWriter).publishOnChainPaymentInitiated(eq("tx-onchain"), eventCaptor.capture());
        OnChainPaymentInitiatedEvent event = eventCaptor.getValue();
        assertEquals("user-123", event.getUserId());
        assertEquals("tx-onchain", event.getTransactionId());
        assertEquals("bc1qrecipient", event.getPaymentRequest().getAddress());
        assertEquals(3_000L, event.getPaymentRequest().getSatsAmount());
        verify(webhookEventService).createPaymentAcceptedEvent(argThat(facts ->
                facts.transactionId().equals("tx-onchain")
                        && facts.userId().equals("user-123")
                        && facts.type() == TransactionType.ONCHAIN_DEBIT
                        && facts.amountSat() == 3_250L
        ));
    }

    @Test
    void payLightningInvoice_internalTransferDelegatesOutboxDetailsToWriter() {
        PayInvoiceRequestDTO request = new PayInvoiceRequestDTO();
        request.setInvoice("lnbc1internal");
        request.setExternalReference("internal-ref");

        when(lightningInvoicePaymentCommand.execute(anyString(), anyString(), same(request), any()))
                .thenAnswer(invocation -> invocation.<java.util.function.Supplier<PaymentResponseDTO>>getArgument(3).get());
        when(invoicesPort.decodeInvoice("lnbc1internal"))
                .thenReturn(new DecodedInvoice("internal-payment-hash", 1_500L, "internal invoice"));
        when(lightningNodePort.findPayment("internal-payment-hash")).thenReturn(Optional.empty());
        when(lightningInvoicePort.findByPaymentHash("internal-payment-hash"))
                .thenReturn(Optional.of(new InternalLightningInvoice("receiver-123", InternalLightningInvoice.InvoiceState.PENDING)));
        when(transactionsPort.createTransaction(any(CreateTransactionRequest.class))).thenReturn(
                TransactionDTOResponse.builder()
                        .id("tx-internal")
                        .status(TransactionStatus.PENDING)
                        .type(TransactionType.LIGHTNING_DEBIT)
                        .build()
        );

        PaymentResponseDTO response = paymentsAdapter.payLightningInvoice(request, "sender-123", "idem-key");

        assertEquals("tx-internal", response.getTransactionId());
        ArgumentCaptor<InternalTransferInitiatedEvent> eventCaptor = ArgumentCaptor.forClass(InternalTransferInitiatedEvent.class);
        verify(outboxWriter).publishInternalTransferInitiated(eq("tx-internal"), eventCaptor.capture());
        InternalTransferInitiatedEvent event = eventCaptor.getValue();
        assertEquals("tx-internal", event.getTransactionId());
        assertEquals("sender-123", event.getSenderId());
        assertEquals("receiver-123", event.getReceiverId());
        assertEquals(1_500L, event.getAmountSat());
        assertEquals("internal-payment-hash", event.getPaymentHash());
    }
}
