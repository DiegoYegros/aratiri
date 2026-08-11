package com.aratiri.infrastructure.messaging.consumer;

import com.aratiri.infrastructure.persistence.jpa.repository.TransactionsRepository;
import com.aratiri.payments.application.dto.OnChainPaymentDTOs;
import com.aratiri.payments.application.dto.PayInvoiceRequestDTO;
import com.aratiri.payments.application.event.OnChainPaymentInitiatedEvent;
import com.aratiri.payments.application.event.PaymentInitiatedEvent;
import com.aratiri.payments.application.port.out.InvoicesPort;
import com.aratiri.payments.application.port.out.LightningNodePort;
import com.aratiri.payments.domain.DecodedInvoice;
import com.aratiri.payments.domain.LightningPayment;
import com.aratiri.payments.domain.LightningPaymentStatus;
import com.aratiri.payments.domain.exception.LightningNodeTransportException;
import com.aratiri.errors.ApplicationException;
import com.aratiri.transactions.application.dto.TransactionDTOResponse;
import com.aratiri.transactions.application.dto.TransactionStatus;
import com.aratiri.transactions.application.port.in.TransactionsPort;
import com.aratiri.webhooks.application.dto.WebhookPayloadData;
import com.aratiri.webhooks.application.port.out.WebhookEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentExecutionServiceTest {

    private static final String TX_ID = "tx-123";
    private static final String USER_ID = "user-456";
    private static final String PAYMENT_HASH = "abc123";

    @Mock
    private TransactionsRepository transactionsRepository;

    @Mock
    private LightningNodePort lightningNodePort;

    @Mock
    private InvoicesPort invoicesPort;

    @Mock
    private TransactionsPort transactionsPort;

    @Mock
    private WebhookEventPublisher webhookEventPublisher;

    private PaymentExecutionService service;

    @BeforeEach
    void setUp() {
        service = new PaymentExecutionService(
                transactionsRepository, lightningNodePort, invoicesPort, transactionsPort, webhookEventPublisher);
        ReflectionTestUtils.setField(service, "defaultFeeLimitSat", 200);
        ReflectionTestUtils.setField(service, "defaultTimeoutSeconds", 200);
    }

    // ── lightning: claim / redelivery safety ──

    @Test
    void executeLightningPayment_completedTransaction_claimFailsAndSkips() {
        when(transactionsRepository.claimPendingForExecution(TX_ID)).thenReturn(0);
        when(transactionsRepository.findById(TX_ID))
                .thenReturn(Optional.of(transactionEntity("COMPLETED")));
        when(transactionsPort.getTransactionById(TX_ID, USER_ID))
                .thenReturn(Optional.of(transaction(TransactionStatus.COMPLETED)));

        service.executeLightningPayment(lightningEvent());

        verifyNoInteractions(lightningNodePort);
        verify(transactionsRepository, never()).releaseExecutionClaim(TX_ID);
        verify(transactionsPort, never()).confirmTransaction(any(), any());
        verify(transactionsPort, never()).failTransaction(any(), any());
    }

    @Test
    void executeLightningPayment_failedTransaction_claimFailsAndSkips() {
        when(transactionsRepository.claimPendingForExecution(TX_ID)).thenReturn(0);
        when(transactionsRepository.findById(TX_ID))
                .thenReturn(Optional.of(transactionEntity("FAILED")));
        when(transactionsPort.getTransactionById(TX_ID, USER_ID))
                .thenReturn(Optional.of(transaction(TransactionStatus.FAILED)));

        service.executeLightningPayment(lightningEvent());

        verifyNoInteractions(lightningNodePort);
    }

    @Test
    void executeLightningPayment_claimHeldByAnotherDelivery_skipsWithoutTouchingLnd() {
        when(transactionsRepository.claimPendingForExecution(TX_ID)).thenReturn(0);
        when(transactionsRepository.findById(TX_ID))
                .thenReturn(Optional.of(transactionEntity("EXECUTING")));
        when(transactionsPort.getTransactionById(TX_ID, USER_ID))
                .thenReturn(Optional.of(transaction(TransactionStatus.PENDING)));

        service.executeLightningPayment(lightningEvent());

        verifyNoInteractions(lightningNodePort);
        verify(transactionsPort, never()).confirmTransaction(any(), any());
        verify(transactionsPort, never()).failTransaction(any(), any());
    }

    @Test
    void executeLightningPayment_missingTransaction_claimFailsAndThrowsRetryable() {
        when(transactionsRepository.claimPendingForExecution(TX_ID)).thenReturn(0);
        when(transactionsPort.getTransactionById(TX_ID, USER_ID)).thenReturn(Optional.empty());
        PaymentInitiatedEvent event = lightningEvent();

        assertThrows(RetryablePaymentExecutionException.class, () -> service.executeLightningPayment(event));
        verifyNoInteractions(lightningNodePort);
    }

    // ── lightning: findPayment pre-check ──

    @Test
    void executeLightningPayment_existingSucceededPayment_recordsFeeAndConfirmsWithoutSending() {
        when(transactionsRepository.claimPendingForExecution(TX_ID)).thenReturn(1);
        when(invoicesPort.decodeInvoice("lnbc1test")).thenReturn(new DecodedInvoice(PAYMENT_HASH, 1000L, "memo"));
        LightningPayment payment = new LightningPayment(LightningPaymentStatus.SUCCEEDED, "FAILURE_REASON_NONE", 7, 0);
        when(lightningNodePort.findPayment(PAYMENT_HASH)).thenReturn(Optional.of(payment));

        service.executeLightningPayment(lightningEvent());

        verify(transactionsPort).addFeeToTransaction(TX_ID, 7);
        verify(transactionsPort).confirmTransaction(TX_ID, USER_ID);
        verify(lightningNodePort, never()).executeLightningPayment(any(), anyInt(), anyInt());
        verify(transactionsRepository, never()).releaseExecutionClaim(TX_ID);
    }

    @Test
    void executeLightningPayment_existingInFlightPayment_throwsRetryableReleasesClaimAndDoesNotSend() {
        when(transactionsRepository.claimPendingForExecution(TX_ID)).thenReturn(1);
        when(invoicesPort.decodeInvoice("lnbc1test")).thenReturn(new DecodedInvoice(PAYMENT_HASH, 1000L, "memo"));
        LightningPayment payment = new LightningPayment(LightningPaymentStatus.IN_FLIGHT, "FAILURE_REASON_NONE", 0, 0);
        when(lightningNodePort.findPayment(PAYMENT_HASH)).thenReturn(Optional.of(payment));

        PaymentInitiatedEvent event = lightningEvent();
        RetryablePaymentExecutionException ex = assertThrows(RetryablePaymentExecutionException.class,
                () -> service.executeLightningPayment(event));

        assertTrue(ex.getMessage().contains("IN_FLIGHT"));
        verify(lightningNodePort, never()).executeLightningPayment(any(), anyInt(), anyInt());
        verify(transactionsRepository).releaseExecutionClaim(TX_ID);
        verify(transactionsPort, never()).confirmTransaction(any(), any());
        verify(transactionsPort, never()).failTransaction(any(), any());
    }

    @Test
    void executeLightningPayment_existingFailedPayment_failsTransactionTerminally() {
        when(transactionsRepository.claimPendingForExecution(TX_ID)).thenReturn(1);
        when(invoicesPort.decodeInvoice("lnbc1test")).thenReturn(new DecodedInvoice(PAYMENT_HASH, 1000L, "memo"));
        LightningPayment payment = new LightningPayment(
                LightningPaymentStatus.FAILED, "FAILURE_REASON_NO_ROUTE", 0, 0);
        when(lightningNodePort.findPayment(PAYMENT_HASH)).thenReturn(Optional.of(payment));

        service.executeLightningPayment(lightningEvent());

        verify(transactionsPort).failTransaction(TX_ID, "FAILURE_REASON_NO_ROUTE");
        verify(transactionsPort, never()).confirmTransaction(any(), any());
        verify(lightningNodePort, never()).executeLightningPayment(any(), anyInt(), anyInt());
        verify(transactionsRepository, never()).releaseExecutionClaim(TX_ID);
    }

    @Test
    void executeLightningPayment_existingUnknownOutcome_throwsRetryableAndReleasesClaim() {
        when(transactionsRepository.claimPendingForExecution(TX_ID)).thenReturn(1);
        when(invoicesPort.decodeInvoice("lnbc1test")).thenReturn(new DecodedInvoice(PAYMENT_HASH, 1000L, "memo"));
        LightningPayment payment = new LightningPayment(LightningPaymentStatus.INITIATED, "FAILURE_REASON_NONE", 0, 0);
        when(lightningNodePort.findPayment(PAYMENT_HASH)).thenReturn(Optional.of(payment));
        PaymentInitiatedEvent event = lightningEvent();

        assertThrows(RetryablePaymentExecutionException.class, () -> service.executeLightningPayment(event));
        verify(lightningNodePort, never()).executeLightningPayment(any(), anyInt(), anyInt());
        verify(transactionsRepository).releaseExecutionClaim(TX_ID);
        verify(transactionsPort, never()).confirmTransaction(any(), any());
        verify(transactionsPort, never()).failTransaction(any(), any());
    }

    @Test
    void executeLightningPayment_findPaymentTransportError_throwsRetryableReleasesClaimAndDoesNotSend() {
        when(transactionsRepository.claimPendingForExecution(TX_ID)).thenReturn(1);
        when(invoicesPort.decodeInvoice("lnbc1test")).thenReturn(new DecodedInvoice(PAYMENT_HASH, 1000L, "memo"));
        when(lightningNodePort.findPayment(PAYMENT_HASH))
                .thenThrow(new LightningNodeTransportException("node down", new RuntimeException("unavailable")));

        PaymentInitiatedEvent event = lightningEvent();
        RetryablePaymentExecutionException ex = assertThrows(RetryablePaymentExecutionException.class,
                () -> service.executeLightningPayment(event));

        assertTrue(ex.getMessage().contains("inspecting payment state"));
        verify(lightningNodePort, never()).executeLightningPayment(any(), anyInt(), anyInt());
        verify(transactionsRepository).releaseExecutionClaim(TX_ID);
        verify(transactionsPort, never()).confirmTransaction(any(), any());
    }

    // ── lightning: send path ──

    @Test
    void executeLightningPayment_noExistingPayment_sendSucceeded_recordsFeeAndConfirms() {
        when(transactionsRepository.claimPendingForExecution(TX_ID)).thenReturn(1);
        when(invoicesPort.decodeInvoice("lnbc1test")).thenReturn(new DecodedInvoice(PAYMENT_HASH, 1000L, "memo"));
        when(lightningNodePort.findPayment(PAYMENT_HASH)).thenReturn(Optional.empty());
        LightningPayment payment = new LightningPayment(LightningPaymentStatus.SUCCEEDED, "FAILURE_REASON_NONE", 0, 1250);
        when(lightningNodePort.executeLightningPayment(any(), eq(200), eq(200))).thenReturn(Optional.of(payment));

        service.executeLightningPayment(lightningEvent());

        verify(transactionsPort).addFeeToTransaction(TX_ID, 2);
        verify(transactionsPort).confirmTransaction(TX_ID, USER_ID);
        verify(transactionsRepository, never()).releaseExecutionClaim(TX_ID);
    }

    @Test
    void executeLightningPayment_sendInFlight_throwsRetryableReleasesClaimWithoutSettlement() {
        when(transactionsRepository.claimPendingForExecution(TX_ID)).thenReturn(1);
        when(invoicesPort.decodeInvoice("lnbc1test")).thenReturn(new DecodedInvoice(PAYMENT_HASH, 1000L, "memo"));
        when(lightningNodePort.findPayment(PAYMENT_HASH)).thenReturn(Optional.empty());
        LightningPayment payment = new LightningPayment(LightningPaymentStatus.IN_FLIGHT, "FAILURE_REASON_NONE", 0, 0);
        when(lightningNodePort.executeLightningPayment(any(), anyInt(), anyInt())).thenReturn(Optional.of(payment));

        PaymentInitiatedEvent event = lightningEvent();
        assertThrows(RetryablePaymentExecutionException.class, () -> service.executeLightningPayment(event));

        verify(transactionsRepository).releaseExecutionClaim(TX_ID);
        verify(transactionsPort, never()).confirmTransaction(any(), any());
        verify(transactionsPort, never()).failTransaction(any(), any());
    }

    @Test
    void executeLightningPayment_sendFailed_failsTransactionTerminally() {
        when(transactionsRepository.claimPendingForExecution(TX_ID)).thenReturn(1);
        when(invoicesPort.decodeInvoice("lnbc1test")).thenReturn(new DecodedInvoice(PAYMENT_HASH, 1000L, "memo"));
        when(lightningNodePort.findPayment(PAYMENT_HASH)).thenReturn(Optional.empty());
        LightningPayment payment = new LightningPayment(LightningPaymentStatus.FAILED, "FAILURE_REASON_TIMEOUT", 0, 0);
        when(lightningNodePort.executeLightningPayment(any(), anyInt(), anyInt())).thenReturn(Optional.of(payment));

        service.executeLightningPayment(lightningEvent());

        verify(transactionsPort).failTransaction(TX_ID, "FAILURE_REASON_TIMEOUT");
        verify(transactionsPort, never()).confirmTransaction(any(), any());
        verify(transactionsRepository, never()).releaseExecutionClaim(TX_ID);
    }

    @Test
    void executeLightningPayment_sendTransportError_throwsRetryableAndReleasesClaim() {
        when(transactionsRepository.claimPendingForExecution(TX_ID)).thenReturn(1);
        when(invoicesPort.decodeInvoice("lnbc1test")).thenReturn(new DecodedInvoice(PAYMENT_HASH, 1000L, "memo"));
        when(lightningNodePort.findPayment(PAYMENT_HASH)).thenReturn(Optional.empty());
        when(lightningNodePort.executeLightningPayment(any(), anyInt(), anyInt()))
                .thenThrow(new LightningNodeTransportException("node down", new RuntimeException("unavailable")));

        PaymentInitiatedEvent event = lightningEvent();
        RetryablePaymentExecutionException ex = assertThrows(RetryablePaymentExecutionException.class,
                () -> service.executeLightningPayment(event));

        assertTrue(ex.getMessage().contains("Exception sending payment"));
        verify(transactionsRepository).releaseExecutionClaim(TX_ID);
        verify(transactionsPort, never()).confirmTransaction(any(), any());
        verify(transactionsPort, never()).failTransaction(any(), any());
    }

    @Test
    void executeLightningPayment_sendNoTerminalResult_throwsRetryableAndReleasesClaim() {
        when(transactionsRepository.claimPendingForExecution(TX_ID)).thenReturn(1);
        when(invoicesPort.decodeInvoice("lnbc1test")).thenReturn(new DecodedInvoice(PAYMENT_HASH, 1000L, "memo"));
        when(lightningNodePort.findPayment(PAYMENT_HASH)).thenReturn(Optional.empty());
        when(lightningNodePort.executeLightningPayment(any(), anyInt(), anyInt())).thenReturn(Optional.empty());
        PaymentInitiatedEvent event = lightningEvent();

        assertThrows(RetryablePaymentExecutionException.class, () -> service.executeLightningPayment(event));
        verify(transactionsRepository).releaseExecutionClaim(TX_ID);
        verify(transactionsPort, never()).confirmTransaction(any(), any());
    }

    @Test
    void executeLightningPayment_failTransactionRace_toleratesAlreadyTerminal() {
        when(transactionsRepository.claimPendingForExecution(TX_ID)).thenReturn(1);
        when(invoicesPort.decodeInvoice("lnbc1test")).thenReturn(new DecodedInvoice(PAYMENT_HASH, 1000L, "memo"));
        LightningPayment payment = new LightningPayment(LightningPaymentStatus.FAILED, "FAILURE_REASON_NO_ROUTE", 0, 0);
        when(lightningNodePort.findPayment(PAYMENT_HASH)).thenReturn(Optional.of(payment));
        doThrow(new ApplicationException("Transaction status [COMPLETED] is not valid for failure."))
                .when(transactionsPort).failTransaction(TX_ID, "FAILURE_REASON_NO_ROUTE");

        service.executeLightningPayment(lightningEvent());
    }

    // ── on-chain ──

    @Test
    void executeOnChainSend_terminalTransaction_claimFailsAndSkips() {
        when(transactionsRepository.claimPendingForExecution(TX_ID)).thenReturn(0);
        when(transactionsRepository.findById(TX_ID))
                .thenReturn(Optional.of(transactionEntity("COMPLETED")));
        when(transactionsPort.getTransactionById(TX_ID, USER_ID))
                .thenReturn(Optional.of(transaction(TransactionStatus.COMPLETED)));

        service.executeOnChainSend(onChainEvent());

        verifyNoInteractions(lightningNodePort);
    }

    @Test
    void executeOnChainSend_claimHeldByAnotherDelivery_skipsWithoutTouchingLnd() {
        when(transactionsRepository.claimPendingForExecution(TX_ID)).thenReturn(0);
        when(transactionsRepository.findById(TX_ID))
                .thenReturn(Optional.of(transactionEntity("EXECUTING")));
        when(transactionsPort.getTransactionById(TX_ID, USER_ID))
                .thenReturn(Optional.of(transaction(TransactionStatus.PENDING)));

        service.executeOnChainSend(onChainEvent());

        verifyNoInteractions(lightningNodePort);
        verify(transactionsPort, never()).confirmTransaction(any(), any());
    }

    @Test
    void executeOnChainSend_labelFound_confirmsWithoutSending() {
        when(transactionsRepository.claimPendingForExecution(TX_ID)).thenReturn(1);
        when(lightningNodePort.findOnChainTxIdByLabel("aratiri-onchain-" + TX_ID))
                .thenReturn(Optional.of("existing-txid"));

        service.executeOnChainSend(onChainEvent());

        verify(transactionsPort).confirmTransaction(TX_ID, USER_ID);
        verify(lightningNodePort, never()).sendOnChain(any(), any());
        verify(transactionsRepository, never()).releaseExecutionClaim(TX_ID);
    }

    @Test
    void executeOnChainSend_noExistingTx_sendSucceeded_confirms() {
        when(transactionsRepository.claimPendingForExecution(TX_ID)).thenReturn(1);
        when(lightningNodePort.findOnChainTxIdByLabel("aratiri-onchain-" + TX_ID)).thenReturn(Optional.empty());
        when(lightningNodePort.sendOnChain(any(), eq("aratiri-onchain-" + TX_ID))).thenReturn("txid123");

        service.executeOnChainSend(onChainEvent());

        ArgumentCaptor<OnChainPaymentDTOs.SendOnChainRequestDTO> requestCaptor =
                ArgumentCaptor.forClass(OnChainPaymentDTOs.SendOnChainRequestDTO.class);
        verify(lightningNodePort).sendOnChain(requestCaptor.capture(), eq("aratiri-onchain-" + TX_ID));
        assertEquals("tb1qdestination", requestCaptor.getValue().getAddress());
        assertEquals(25_000L, requestCaptor.getValue().getSatsAmount());
        verify(transactionsPort).confirmTransaction(TX_ID, USER_ID);
        verify(transactionsRepository, never()).releaseExecutionClaim(TX_ID);
    }

    @Test
    void executeOnChainSend_blankTxid_throwsRetryableReleasesClaimWithoutConfirming() {
        when(transactionsRepository.claimPendingForExecution(TX_ID)).thenReturn(1);
        when(lightningNodePort.findOnChainTxIdByLabel("aratiri-onchain-" + TX_ID)).thenReturn(Optional.empty());
        when(lightningNodePort.sendOnChain(any(), any())).thenReturn(" ");
        OnChainPaymentInitiatedEvent event = onChainEvent();

        assertThrows(RetryablePaymentExecutionException.class, () -> service.executeOnChainSend(event));
        verify(transactionsRepository).releaseExecutionClaim(TX_ID);
        verify(transactionsPort, never()).confirmTransaction(any(), any());
    }

    @Test
    void executeOnChainSend_sendTransportError_throwsRetryableAndReleasesClaim() {
        when(transactionsRepository.claimPendingForExecution(TX_ID)).thenReturn(1);
        when(lightningNodePort.findOnChainTxIdByLabel("aratiri-onchain-" + TX_ID)).thenReturn(Optional.empty());
        when(lightningNodePort.sendOnChain(any(), any()))
                .thenThrow(new LightningNodeTransportException("node down", new RuntimeException("unavailable")));
        OnChainPaymentInitiatedEvent event = onChainEvent();

        assertThrows(RetryablePaymentExecutionException.class, () -> service.executeOnChainSend(event));
        verify(transactionsRepository).releaseExecutionClaim(TX_ID);
        verify(transactionsPort, never()).confirmTransaction(any(), any());
    }

    @Test
    void executeOnChainSend_labelLookupError_throwsRetryableReleasesClaimWithoutSending() {
        when(transactionsRepository.claimPendingForExecution(TX_ID)).thenReturn(1);
        when(lightningNodePort.findOnChainTxIdByLabel("aratiri-onchain-" + TX_ID))
                .thenThrow(new LightningNodeTransportException("node down", new RuntimeException("unavailable")));
        OnChainPaymentInitiatedEvent event = onChainEvent();

        assertThrows(RetryablePaymentExecutionException.class, () -> service.executeOnChainSend(event));
        verify(lightningNodePort, never()).sendOnChain(any(), any());
        verify(transactionsRepository).releaseExecutionClaim(TX_ID);
        verify(transactionsPort, never()).confirmTransaction(any(), any());
    }

    // ── DLT / unknown outcome webhook ──

    @Test
    void handleLightningUnknownOutcome_buildsWebhookFacts() {
        when(transactionsPort.getTransactionById(TX_ID, USER_ID))
                .thenReturn(Optional.of(transaction(TransactionStatus.PENDING)));
        when(invoicesPort.decodeInvoice("lnbc1test")).thenReturn(new DecodedInvoice(PAYMENT_HASH, 1000L, "memo"));

        service.handleLightningUnknownOutcome(lightningEvent(), "Exception sending payment: node down", 5);

        WebhookPayloadData facts = capturedFacts();
        assertEquals(TX_ID, facts.getOperationId());
        assertEquals(TX_ID, facts.getTransactionId());
        assertEquals(USER_ID, facts.getUserId());
        assertEquals("LIGHTNING_PAYMENT", facts.getOperationType());
        assertEquals("UNKNOWN_OUTCOME", facts.getOperationStatus());
        assertEquals(PAYMENT_HASH, facts.getReferenceId());
        assertEquals(5, facts.getAttemptCount());
        assertEquals("Exception sending payment: node down", facts.getOperationError());
        assertEquals(1000L, facts.getAmountSat());
        assertEquals(TransactionStatus.PENDING.name(), facts.getStatus());
        assertEquals("external-1", facts.getExternalReference());
        assertEquals("{\"order\":\"123\"}", facts.getMetadata());
    }

    @Test
    void handleLightningUnknownOutcome_reportsDeliveryAttemptWhenProvided() {
        when(transactionsPort.getTransactionById(TX_ID, USER_ID))
                .thenReturn(Optional.of(transaction(TransactionStatus.PENDING)));
        when(invoicesPort.decodeInvoice("lnbc1test")).thenReturn(new DecodedInvoice(PAYMENT_HASH, 1000L, "memo"));

        service.handleLightningUnknownOutcome(lightningEvent(), "node down", 4);

        assertEquals(4, capturedFacts().getAttemptCount());
    }

    @Test
    void handleLightningUnknownOutcome_missingTransaction_emitsFactsWithNulls() {
        when(transactionsPort.getTransactionById(TX_ID, USER_ID)).thenReturn(Optional.empty());

        service.handleLightningUnknownOutcome(lightningEvent(), "transaction missing", 5);

        WebhookPayloadData facts = capturedFacts();
        assertEquals(TX_ID, facts.getOperationId());
        assertEquals("LIGHTNING_PAYMENT", facts.getOperationType());
        assertNull(facts.getAmountSat());
        assertNull(facts.getStatus());
    }

    @Test
    void handleOnChainUnknownOutcome_buildsWebhookFacts() {
        when(transactionsPort.getTransactionById(TX_ID, USER_ID))
                .thenReturn(Optional.of(transaction(TransactionStatus.PENDING)));

        service.handleOnChainUnknownOutcome(onChainEvent(), "LND sendCoins returned no transaction id", 5);

        WebhookPayloadData facts = capturedFacts();
        assertEquals(TX_ID, facts.getOperationId());
        assertEquals("ONCHAIN_SEND", facts.getOperationType());
        assertEquals("UNKNOWN_OUTCOME", facts.getOperationStatus());
        assertEquals(5, facts.getAttemptCount());
        assertEquals("LND sendCoins returned no transaction id", facts.getOperationError());
        assertEquals(1000L, facts.getAmountSat());
        assertEquals(TransactionStatus.PENDING.name(), facts.getStatus());
        assertEquals("external-1", facts.getExternalReference());
    }

    private WebhookPayloadData capturedFacts() {
        ArgumentCaptor<WebhookPayloadData> captor = ArgumentCaptor.forClass(WebhookPayloadData.class);
        verify(webhookEventPublisher).publishWebhookEvent(
                any(), any(), any(), any(), any(), any(), captor.capture());
        return captor.getValue();
    }

    private PaymentInitiatedEvent lightningEvent() {
        PayInvoiceRequestDTO request = new PayInvoiceRequestDTO();
        request.setInvoice("lnbc1test");
        request.setFeeLimitSat(101L);
        request.setTimeoutSeconds(22);
        request.setExternalReference("external-1");
        request.setMetadata("{\"order\":\"123\"}");
        return new PaymentInitiatedEvent(USER_ID, TX_ID, request);
    }

    private OnChainPaymentInitiatedEvent onChainEvent() {
        OnChainPaymentDTOs.SendOnChainRequestDTO request = new OnChainPaymentDTOs.SendOnChainRequestDTO();
        request.setAddress("tb1qdestination");
        request.setSatsAmount(25_000L);
        request.setSatPerVbyte(18L);
        request.setTargetConf(4);
        request.setExternalReference("external-1");
        request.setMetadata("{\"order\":\"123\"}");
        return new OnChainPaymentInitiatedEvent(USER_ID, TX_ID, request);
    }

    private static com.aratiri.infrastructure.persistence.jpa.entity.TransactionEntity transactionEntity(String currentStatus) {
        com.aratiri.infrastructure.persistence.jpa.entity.TransactionEntity entity =
                new com.aratiri.infrastructure.persistence.jpa.entity.TransactionEntity();
        entity.setId(TX_ID);
        entity.setUserId(USER_ID);
        entity.setCurrentStatus(currentStatus);
        return entity;
    }

    private static TransactionDTOResponse transaction(TransactionStatus status) {
        return TransactionDTOResponse.builder()
                .id(TX_ID)
                .userId(USER_ID)
                .amountSat(1000L)
                .status(status)
                .referenceId("transaction-ref-1")
                .externalReference("external-1")
                .metadata("{\"order\":\"123\"}")
                .build();
    }
}
