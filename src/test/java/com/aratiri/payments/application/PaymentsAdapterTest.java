package com.aratiri.payments.application;

import com.aratiri.infrastructure.messaging.outbox.OutboxWriter;
import com.aratiri.payments.application.command.LightningInvoicePaymentCommandService;
import com.aratiri.payments.application.command.OnChainPaymentCommandService;
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
import com.aratiri.shared.exception.AratiriException;
import com.aratiri.transactions.application.dto.CreateTransactionRequest;
import com.aratiri.transactions.application.dto.TransactionDTOResponse;
import com.aratiri.transactions.application.dto.TransactionCurrency;
import com.aratiri.transactions.application.dto.TransactionStatus;
import com.aratiri.transactions.application.dto.TransactionType;
import com.aratiri.transactions.application.event.InternalTransferInitiatedEvent;
import com.aratiri.webhooks.application.WebhookEventService;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
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
  private LightningInvoicePaymentCommandService lightningInvoicePaymentCommand;

  @Mock
  private OnChainPaymentCommandService onChainPaymentCommand;

  @Mock
  private WebhookEventService webhookEventService;

  private PaymentsAdapter paymentsAdapter;

  private static final String USER_ID = "user-123";
  private static final String PAYMENT_HASH = "payment-hash-123";

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

  // ── payLightningInvoice – external payment (existing) ──

  @Test
  void payLightningInvoice_externalPaymentDelegatesOutboxDetailsToWriter() {
    PayInvoiceRequestDTO request = new PayInvoiceRequestDTO();
    request.setInvoice("lnbc1external");
    request.setExternalReference("external-ref");
    request.setMetadata("{\"orderId\":\"order-123\"}");

    when(lightningInvoicePaymentCommand.execute(anyString(), anyString(), same(request), any()))
        .thenAnswer(invocation -> invocation.<java.util.function.Supplier<PaymentResponseDTO>>getArgument(3).get());
    when(invoicesPort.decodeInvoice("lnbc1external"))
        .thenReturn(new DecodedInvoice(PAYMENT_HASH, 2_000L, "test invoice"));
    when(lightningNodePort.findPayment(PAYMENT_HASH)).thenReturn(Optional.empty());
    when(lightningInvoicePort.findByPaymentHash(PAYMENT_HASH)).thenReturn(Optional.empty());
    when(invoicesPort.existsSettledInvoice(PAYMENT_HASH)).thenReturn(false);
    when(transactionsPort.createTransaction(any(CreateTransactionRequest.class))).thenReturn(
        TransactionDTOResponse.builder()
            .id("tx-123")
            .status(TransactionStatus.PENDING)
            .type(TransactionType.LIGHTNING_DEBIT)
            .build()
    );

    PaymentResponseDTO response = paymentsAdapter.payLightningInvoice(request, USER_ID, "idem-key");

    assertEquals("tx-123", response.getTransactionId());
    ArgumentCaptor<PaymentInitiatedEvent> eventCaptor = ArgumentCaptor.forClass(PaymentInitiatedEvent.class);
    verify(outboxWriter).publishPaymentInitiated(eq("tx-123"), eventCaptor.capture());
    PaymentInitiatedEvent event = eventCaptor.getValue();
    assertEquals(USER_ID, event.getUserId());
    assertEquals("tx-123", event.getTransactionId());
    assertEquals(request, event.getPayRequest());
    verify(webhookEventService).createPaymentAcceptedEvent(argThat(facts ->
        facts.transactionId().equals("tx-123")
            && facts.userId().equals(USER_ID)
            && facts.type() == TransactionType.LIGHTNING_DEBIT
            && facts.amountSat() == 2_000L
            && facts.referenceId().equals(PAYMENT_HASH)
    ));
  }

  // ── sendOnChain (existing) ──

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
    when(accountsPort.getAccount(USER_ID))
        .thenReturn(new PaymentAccount(USER_ID, 10_000L, "bc1qsender"));
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

    OnChainPaymentDTOs.SendOnChainResponseDTO response = paymentsAdapter.sendOnChain(request, USER_ID, "idem-key");

    assertEquals("tx-onchain", response.getTransactionId());
    ArgumentCaptor<OnChainPaymentInitiatedEvent> eventCaptor = ArgumentCaptor.forClass(OnChainPaymentInitiatedEvent.class);
    verify(outboxWriter).publishOnChainPaymentInitiated(eq("tx-onchain"), eventCaptor.capture());
    OnChainPaymentInitiatedEvent event = eventCaptor.getValue();
    assertEquals(USER_ID, event.getUserId());
    assertEquals("tx-onchain", event.getTransactionId());
    assertEquals("bc1qrecipient", event.getPaymentRequest().getAddress());
    assertEquals(3_000L, event.getPaymentRequest().getSatsAmount());
    verify(webhookEventService).createPaymentAcceptedEvent(argThat(facts ->
        facts.transactionId().equals("tx-onchain")
            && facts.userId().equals(USER_ID)
            && facts.type() == TransactionType.ONCHAIN_DEBIT
            && facts.amountSat() == 3_250L
    ));
  }

  // ── payLightningInvoice – internal transfer (existing) ──

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

  // ── payLightningInvoiceInternal ──

  @Test
  void payLightningInvoiceInternal_shouldExecutePaymentDirectly() {
    PayInvoiceRequestDTO request = new PayInvoiceRequestDTO();
    request.setInvoice("lnbc1direct");

    when(invoicesPort.decodeInvoice("lnbc1direct"))
        .thenReturn(new DecodedInvoice(PAYMENT_HASH, 1_000L, "direct invoice"));
    when(lightningNodePort.findPayment(PAYMENT_HASH)).thenReturn(Optional.empty());
    when(lightningInvoicePort.findByPaymentHash(PAYMENT_HASH)).thenReturn(Optional.empty());
    when(invoicesPort.existsSettledInvoice(PAYMENT_HASH)).thenReturn(false);
    when(transactionsPort.createTransaction(any(CreateTransactionRequest.class))).thenReturn(
        TransactionDTOResponse.builder()
            .id("tx-direct")
            .status(TransactionStatus.PENDING)
            .type(TransactionType.LIGHTNING_DEBIT)
            .build()
    );

    PaymentResponseDTO response = paymentsAdapter.payLightningInvoiceInternal(request, USER_ID);

    assertEquals("tx-direct", response.getTransactionId());
    assertEquals(TransactionStatus.PENDING, response.getStatus());
    verifyNoInteractions(lightningInvoicePaymentCommand);
    verify(outboxWriter).publishPaymentInitiated(eq("tx-direct"), any());
  }

  // ── payLightningInvoice – node payment already succeeded → CONFLICT ──

  @Test
  void payLightningInvoice_nodePaymentSucceeded_throwsConflict() {
    PayInvoiceRequestDTO request = new PayInvoiceRequestDTO();
    request.setInvoice("lnbc1alreadypaid");

    when(lightningInvoicePaymentCommand.execute(anyString(), anyString(), same(request), any()))
        .thenAnswer(invocation -> invocation.<java.util.function.Supplier<PaymentResponseDTO>>getArgument(3).get());
    when(invoicesPort.decodeInvoice("lnbc1alreadypaid"))
        .thenReturn(new DecodedInvoice(PAYMENT_HASH, 1_000L, "paid invoice"));

    lnrpc.Payment succeededPayment = mock(lnrpc.Payment.class);
    when(succeededPayment.getStatus()).thenReturn(lnrpc.Payment.PaymentStatus.SUCCEEDED);
    when(lightningNodePort.findPayment(PAYMENT_HASH)).thenReturn(Optional.of(succeededPayment));

    AratiriException ex = assertThrows(AratiriException.class,
        () -> paymentsAdapter.payLightningInvoice(request, USER_ID, "idem-key"));

    assertEquals(409, ex.getStatus());
    assertTrue(ex.getMessage().contains("already in progress or has been settled"));
    verifyNoInteractions(transactionsPort);
  }

  // ── payLightningInvoice – node payment in-flight → CONFLICT ──

  @Test
  void payLightningInvoice_nodePaymentInFlight_throwsConflict() {
    PayInvoiceRequestDTO request = new PayInvoiceRequestDTO();
    request.setInvoice("lnbc1inflight");

    when(lightningInvoicePaymentCommand.execute(anyString(), anyString(), same(request), any()))
        .thenAnswer(invocation -> invocation.<java.util.function.Supplier<PaymentResponseDTO>>getArgument(3).get());
    when(invoicesPort.decodeInvoice("lnbc1inflight"))
        .thenReturn(new DecodedInvoice(PAYMENT_HASH, 1_000L, "in-flight invoice"));

    lnrpc.Payment inFlightPayment = mock(lnrpc.Payment.class);
    when(inFlightPayment.getStatus()).thenReturn(lnrpc.Payment.PaymentStatus.IN_FLIGHT);
    when(lightningNodePort.findPayment(PAYMENT_HASH)).thenReturn(Optional.of(inFlightPayment));

    AratiriException ex = assertThrows(AratiriException.class,
        () -> paymentsAdapter.payLightningInvoice(request, USER_ID, "idem-key"));

    assertEquals(409, ex.getStatus());
    verifyNoInteractions(transactionsPort);
  }

  // ── payLightningInvoice – internal invoice already settled → BAD_REQUEST ──

  @Test
  void payLightningInvoice_internalInvoiceSettled_throwsBadRequest() {
    PayInvoiceRequestDTO request = new PayInvoiceRequestDTO();
    request.setInvoice("lnbc1settled");

    when(lightningInvoicePaymentCommand.execute(anyString(), anyString(), same(request), any()))
        .thenAnswer(invocation -> invocation.<java.util.function.Supplier<PaymentResponseDTO>>getArgument(3).get());
    when(invoicesPort.decodeInvoice("lnbc1settled"))
        .thenReturn(new DecodedInvoice(PAYMENT_HASH, 1_000L, "settled internal"));
    when(lightningNodePort.findPayment(PAYMENT_HASH)).thenReturn(Optional.empty());
    when(lightningInvoicePort.findByPaymentHash(PAYMENT_HASH))
        .thenReturn(Optional.of(new InternalLightningInvoice("receiver-123", InternalLightningInvoice.InvoiceState.SETTLED)));

    AratiriException ex = assertThrows(AratiriException.class,
        () -> paymentsAdapter.payLightningInvoice(request, USER_ID, "idem-key"));

    assertEquals(400, ex.getStatus());
    assertTrue(ex.getMessage().contains("already paid"));
  }

  // ── payLightningInvoice – internal invoice self-payment → BAD_REQUEST ──

  @Test
  void payLightningInvoice_internalInvoiceSelfPayment_throwsBadRequest() {
    PayInvoiceRequestDTO request = new PayInvoiceRequestDTO();
    request.setInvoice("lnbc1self");

    when(lightningInvoicePaymentCommand.execute(anyString(), anyString(), same(request), any()))
        .thenAnswer(invocation -> invocation.<java.util.function.Supplier<PaymentResponseDTO>>getArgument(3).get());
    when(invoicesPort.decodeInvoice("lnbc1self"))
        .thenReturn(new DecodedInvoice(PAYMENT_HASH, 1_000L, "self payment"));
    when(lightningNodePort.findPayment(PAYMENT_HASH)).thenReturn(Optional.empty());
    when(lightningInvoicePort.findByPaymentHash(PAYMENT_HASH))
        .thenReturn(Optional.of(new InternalLightningInvoice(USER_ID, InternalLightningInvoice.InvoiceState.PENDING)));

    AratiriException ex = assertThrows(AratiriException.class,
        () -> paymentsAdapter.payLightningInvoice(request, USER_ID, "idem-key"));

    assertEquals(400, ex.getStatus());
    assertTrue(ex.getMessage().contains("Payment to self"));
  }

  // ── payLightningInvoice – Aratiri invoice already settled → BAD_REQUEST ──

  @Test
  void payLightningInvoice_settledAratiriInvoice_throwsBadRequest() {
    PayInvoiceRequestDTO request = new PayInvoiceRequestDTO();
    request.setInvoice("lnbc1aratirisettled");

    when(lightningInvoicePaymentCommand.execute(anyString(), anyString(), same(request), any()))
        .thenAnswer(invocation -> invocation.<java.util.function.Supplier<PaymentResponseDTO>>getArgument(3).get());
    when(invoicesPort.decodeInvoice("lnbc1aratirisettled"))
        .thenReturn(new DecodedInvoice(PAYMENT_HASH, 1_000L, "settled aratiri"));
    when(lightningNodePort.findPayment(PAYMENT_HASH)).thenReturn(Optional.empty());
    when(lightningInvoicePort.findByPaymentHash(PAYMENT_HASH)).thenReturn(Optional.empty());
    when(invoicesPort.existsSettledInvoice(PAYMENT_HASH)).thenReturn(true);

    AratiriException ex = assertThrows(AratiriException.class,
        () -> paymentsAdapter.payLightningInvoice(request, USER_ID, "idem-key"));

    assertEquals(400, ex.getStatus());
    assertTrue(ex.getMessage().contains("already been paid"));
  }

  // ── payLightningInvoice – external payment already succeeded on node (second check) → BAD_REQUEST ──

  @Test
  void payLightningInvoice_externalPaymentAlreadySucceededOnNode_throwsBadRequest() {
    PayInvoiceRequestDTO request = new PayInvoiceRequestDTO();
    request.setInvoice("lnbc1nodealreadypaid");

    when(lightningInvoicePaymentCommand.execute(anyString(), anyString(), same(request), any()))
        .thenAnswer(invocation -> invocation.<java.util.function.Supplier<PaymentResponseDTO>>getArgument(3).get());
    when(invoicesPort.decodeInvoice("lnbc1nodealreadypaid"))
        .thenReturn(new DecodedInvoice(PAYMENT_HASH, 1_000L, "node paid"));

    lnrpc.Payment succeededPayment = mock(lnrpc.Payment.class);
    when(succeededPayment.getStatus()).thenReturn(lnrpc.Payment.PaymentStatus.SUCCEEDED);
    when(lightningNodePort.findPayment(PAYMENT_HASH))
        .thenReturn(Optional.empty(), Optional.of(succeededPayment));
    when(lightningInvoicePort.findByPaymentHash(PAYMENT_HASH)).thenReturn(Optional.empty());
    when(invoicesPort.existsSettledInvoice(PAYMENT_HASH)).thenReturn(false);

    AratiriException ex = assertThrows(AratiriException.class,
        () -> paymentsAdapter.payLightningInvoice(request, USER_ID, "idem-key"));

    assertEquals(400, ex.getStatus());
    assertTrue(ex.getMessage().contains("already been paid"));
  }

  // ── checkPaymentStatusOnNode ──

  @Test
  void checkPaymentStatusOnNode_found_returnsPayment() {
    lnrpc.Payment payment = mock(lnrpc.Payment.class);
    when(lightningNodePort.findPayment(PAYMENT_HASH)).thenReturn(Optional.of(payment));

    Optional<lnrpc.Payment> result = paymentsAdapter.checkPaymentStatusOnNode(PAYMENT_HASH);

    assertTrue(result.isPresent());
    assertSame(payment, result.get());
  }

  @Test
  void checkPaymentStatusOnNode_notFound_returnsEmpty() {
    when(lightningNodePort.findPayment(PAYMENT_HASH))
        .thenThrow(new StatusRuntimeException(Status.NOT_FOUND));

    Optional<lnrpc.Payment> result = paymentsAdapter.checkPaymentStatusOnNode(PAYMENT_HASH);

    assertTrue(result.isEmpty());
  }

  @Test
  void checkPaymentStatusOnNode_grpcError_throwsException() {
    when(lightningNodePort.findPayment(PAYMENT_HASH))
        .thenThrow(new StatusRuntimeException(Status.INTERNAL));

    AratiriException ex = assertThrows(AratiriException.class,
        () -> paymentsAdapter.checkPaymentStatusOnNode(PAYMENT_HASH));

    assertEquals(500, ex.getStatus());
    assertTrue(ex.getMessage().contains("gRPC error"));
  }

  @Test
  void checkPaymentStatusOnNode_genericError_throwsException() {
    when(lightningNodePort.findPayment(PAYMENT_HASH))
        .thenThrow(new RuntimeException("network failure"));

    AratiriException ex = assertThrows(AratiriException.class,
        () -> paymentsAdapter.checkPaymentStatusOnNode(PAYMENT_HASH));

    assertEquals(500, ex.getStatus());
    assertTrue(ex.getMessage().contains("gRPC error"));
  }

  // ── sendOnChain – self payment ──

  @Test
  void sendOnChain_selfPayment_throwsBadRequest() {
    OnChainPaymentDTOs.SendOnChainRequestDTO request = new OnChainPaymentDTOs.SendOnChainRequestDTO();
    request.setAddress("bc1qsender");
    request.setSatsAmount(3_000L);
    request.setTargetConf(3);

    when(onChainPaymentCommand.execute(anyString(), anyString(), same(request), any()))
        .thenAnswer(invocation -> invocation.<java.util.function.Supplier<OnChainPaymentDTOs.SendOnChainResponseDTO>>getArgument(3).get());
    when(accountsPort.getAccount(USER_ID))
        .thenReturn(new PaymentAccount(USER_ID, 10_000L, "bc1qsender"));
    when(lightningNodePort.estimateOnChainFee(any(OnChainPaymentDTOs.EstimateFeeRequestDTO.class)))
        .thenReturn(new OnChainFeeEstimate(250L, 12L));

    AratiriException ex = assertThrows(AratiriException.class,
        () -> paymentsAdapter.sendOnChain(request, USER_ID, "idem-key"));

    assertEquals(400, ex.getStatus());
    assertTrue(ex.getMessage().contains("Payment to self"));
    verifyNoInteractions(transactionsPort);
  }

  // ── sendOnChain – without bitcoin: prefix ──

  @Test
  void sendOnChain_withoutBitcoinPrefix_usesAddressAsIs() {
    OnChainPaymentDTOs.SendOnChainRequestDTO request = new OnChainPaymentDTOs.SendOnChainRequestDTO();
    request.setAddress("bc1qplain");
    request.setSatsAmount(1_000L);
    request.setTargetConf(1);

    when(onChainPaymentCommand.execute(anyString(), anyString(), same(request), any()))
        .thenAnswer(invocation -> invocation.<java.util.function.Supplier<OnChainPaymentDTOs.SendOnChainResponseDTO>>getArgument(3).get());
    when(accountsPort.getAccount(USER_ID))
        .thenReturn(new PaymentAccount(USER_ID, 5_000L, "bc1qsender"));
    when(lightningNodePort.estimateOnChainFee(any(OnChainPaymentDTOs.EstimateFeeRequestDTO.class)))
        .thenReturn(new OnChainFeeEstimate(100L, 5L));
    when(transactionsPort.createTransaction(any(CreateTransactionRequest.class))).thenReturn(
        TransactionDTOResponse.builder()
            .id("tx-plain")
            .status(TransactionStatus.PENDING)
            .type(TransactionType.ONCHAIN_DEBIT)
            .build()
    );

    OnChainPaymentDTOs.SendOnChainResponseDTO response = paymentsAdapter.sendOnChain(request, USER_ID, "idem-key");

    assertEquals("tx-plain", response.getTransactionId());
    verify(outboxWriter).publishOnChainPaymentInitiated(eq("tx-plain"), any());
  }

  // ── estimateOnChainFee ──

  @Test
  void estimateOnChainFee_success_returnsFeeResponse() {
    OnChainPaymentDTOs.EstimateFeeRequestDTO request = new OnChainPaymentDTOs.EstimateFeeRequestDTO();
    request.setAddress("bc1qrecipient");
    request.setSatsAmount(10_000L);
    request.setTargetConf(6);

    when(lightningNodePort.estimateOnChainFee(any(OnChainPaymentDTOs.EstimateFeeRequestDTO.class)))
        .thenReturn(new OnChainFeeEstimate(500L, 15L));

    OnChainPaymentDTOs.EstimateFeeResponseDTO response = paymentsAdapter.estimateOnChainFee(request, USER_ID);

    assertEquals(500L, response.getFeeSat());
    assertEquals(15L, response.getSatPerVbyte());
    assertEquals(0L, response.getPlatformFeeSat());
    assertEquals(500L, response.getTotalFeeSat());
  }

  @Test
  void estimateOnChainFee_withBitcoinPrefix_stripsPrefix() {
    OnChainPaymentDTOs.EstimateFeeRequestDTO request = new OnChainPaymentDTOs.EstimateFeeRequestDTO();
    request.setAddress("bitcoin:bc1qrecipient");
    request.setSatsAmount(5_000L);
    request.setTargetConf(3);

    ArgumentCaptor<OnChainPaymentDTOs.EstimateFeeRequestDTO> captor =
        ArgumentCaptor.forClass(OnChainPaymentDTOs.EstimateFeeRequestDTO.class);
    when(lightningNodePort.estimateOnChainFee(captor.capture()))
        .thenReturn(new OnChainFeeEstimate(200L, 10L));

    paymentsAdapter.estimateOnChainFee(request, USER_ID);

    assertEquals("bc1qrecipient", captor.getValue().getAddress());
    assertEquals(5_000L, captor.getValue().getSatsAmount());
  }

  @Test
  void estimateOnChainFee_error_throwsException() {
    OnChainPaymentDTOs.EstimateFeeRequestDTO request = new OnChainPaymentDTOs.EstimateFeeRequestDTO();
    request.setAddress("bc1qinvalid");
    request.setSatsAmount(1_000L);

    when(lightningNodePort.estimateOnChainFee(any(OnChainPaymentDTOs.EstimateFeeRequestDTO.class)))
        .thenThrow(new RuntimeException("fee estimation failed"));

    AratiriException ex = assertThrows(AratiriException.class,
        () -> paymentsAdapter.estimateOnChainFee(request, USER_ID));

    assertEquals(500, ex.getStatus());
    assertTrue(ex.getMessage().contains("Error estimating fee"));
  }
}
