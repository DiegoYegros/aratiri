package com.aratiri.infrastructure.nodeoperations;

import com.aratiri.infrastructure.configuration.NodeOperationProperties;
import com.aratiri.infrastructure.persistence.jpa.entity.NodeOperationEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.NodeOperationStatus;
import com.aratiri.infrastructure.persistence.jpa.entity.NodeOperationType;
import com.aratiri.infrastructure.persistence.jpa.repository.NodeOperationsRepository;
import com.aratiri.payments.application.dto.PayInvoiceRequestDTO;
import com.aratiri.payments.application.port.out.InvoicesPort;
import com.aratiri.payments.application.port.out.LightningNodePort;
import com.aratiri.payments.domain.DecodedInvoice;
import com.aratiri.payments.domain.LightningPayment;
import com.aratiri.payments.domain.LightningPaymentStatus;
import com.aratiri.payments.domain.exception.LightningNodeTransportException;
import com.aratiri.payments.infrastructure.json.JsonUtils;
import com.aratiri.shared.exception.AratiriException;
import com.aratiri.transactions.application.dto.TransactionDTOResponse;
import com.aratiri.transactions.application.dto.TransactionStatus;
import com.aratiri.transactions.application.port.in.TransactionsPort;
import com.aratiri.webhooks.application.NodeOperationUnknownOutcomeFacts;
import com.aratiri.webhooks.application.WebhookEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NodeOperationServiceTest {

    @Mock
    private NodeOperationsRepository nodeOperationsRepository;

    @Mock
    private LightningNodePort lightningNodePort;

    @Mock
    private InvoicesPort invoicesPort;

    @Mock
    private TransactionsPort transactionsPort;

    @Mock
    private NodeOperationClaimer claimer;

    @Mock
    private WebhookEventService webhookEventService;

    private NodeOperationProperties nodeOperationProperties;
    private NodeOperationService service;

    private static final String TX_ID = "tx-123";
    private static final String USER_ID = "user-456";
    private static final String PAYMENT_HASH = "abc123";

    @BeforeEach
    void setUp() {
        nodeOperationProperties = new NodeOperationProperties();
        nodeOperationProperties.setFixedDelayMs(1_000);
        nodeOperationProperties.setLightningMaxAttempts(5);
        nodeOperationProperties.setOnchainMaxAttempts(5);

        NodeOperationState stateManager = new NodeOperationState(nodeOperationsRepository, transactionsPort, nodeOperationProperties);
        NodeOperationRetryPolicy retryPolicy = new NodeOperationRetryPolicy(nodeOperationProperties);
        service = new NodeOperationService(
                nodeOperationsRepository,
                lightningNodePort,
                invoicesPort,
                stateManager,
                retryPolicy,
                claimer,
                webhookEventService
        );
        ReflectionTestUtils.setField(service, "defaultFeeLimitSat", 200);
        ReflectionTestUtils.setField(service, "defaultTimeoutSeconds", 200);
    }

    @Test
    void enqueueLightningPayment_storesDurableFactWithDecodedPaymentHashAndIsIdempotentByTransactionId() {
        LightningPaymentOperation payment = lightningPayment();
        when(nodeOperationsRepository.findByTransactionId(TX_ID)).thenReturn(Optional.empty());
        when(invoicesPort.decodeInvoice("lightning:lnbc1test"))
                .thenReturn(new DecodedInvoice(PAYMENT_HASH, 1_000L, "memo"));
        when(nodeOperationsRepository.save(any(NodeOperationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NodeOperationEntity saved = service.enqueueLightningPayment(payment);

        assertEquals(TX_ID, saved.getTransactionId());
        assertEquals(USER_ID, saved.getUserId());
        assertEquals(NodeOperationType.LIGHTNING_PAYMENT, saved.getOperationType());
        assertEquals(NodeOperationStatus.PENDING, saved.getStatus());
        assertEquals(PAYMENT_HASH, saved.getReferenceId());
        assertEquals(0, saved.getAttemptCount());
        assertNotNull(saved.getNextAttemptAt());

        LightningPaymentOperation payload = JsonUtils.fromJson(saved.getRequestPayload(), LightningPaymentOperation.class);
        assertEquals(TX_ID, payload.transactionId());
        assertEquals(USER_ID, payload.userId());
        assertEquals(PAYMENT_HASH, payload.paymentHash());
        assertEquals("lightning:lnbc1test", payload.invoice());
        assertEquals(101L, payload.feeLimitSat());
        assertEquals(22, payload.timeoutSeconds());
        assertEquals("external-1", payload.externalReference());
        assertEquals("{\"order\":\"123\"}", payload.metadata());

        NodeOperationEntity existing = NodeOperationEntity.builder()
                .transactionId(TX_ID)
                .userId(USER_ID)
                .operationType(NodeOperationType.LIGHTNING_PAYMENT)
                .status(NodeOperationStatus.PENDING)
                .referenceId(PAYMENT_HASH)
                .requestPayload(saved.getRequestPayload())
                .attemptCount(0)
                .build();
        when(nodeOperationsRepository.findByTransactionId(TX_ID)).thenReturn(Optional.of(existing));

        assertSame(existing, service.enqueueLightningPayment(payment));
        verify(nodeOperationsRepository, times(1)).save(any(NodeOperationEntity.class));
        verify(invoicesPort, times(1)).decodeInvoice("lightning:lnbc1test");
    }

    @Test
    void lightning_existingSucceededPayment_settlesAndMarksSucceededWithoutSending() {
        NodeOperationEntity op = buildLightningOperation(NodeOperationStatus.IN_PROGRESS, 1);
        LightningPayment payment = new LightningPayment(LightningPaymentStatus.SUCCEEDED, "FAILURE_REASON_NONE", 7, 0);
        when(lightningNodePort.findPayment(PAYMENT_HASH)).thenReturn(Optional.of(payment));

        service.executeLightning(op);

        assertEquals(NodeOperationStatus.SUCCEEDED, op.getStatus());
        assertNotNull(op.getCompletedAt());
        verify(transactionsPort).addFeeToTransaction(TX_ID, 7);
        verify(transactionsPort).confirmTransaction(TX_ID, USER_ID);
        verify(lightningNodePort, never()).executeLightningPayment(any(), anyInt(), anyInt());
        verify(nodeOperationsRepository).save(op);
    }

    @Test
    void lightning_existingInFlightPayment_recordsRetryableStateWithoutSendingOrSettling() {
        NodeOperationEntity op = buildLightningOperation(NodeOperationStatus.IN_PROGRESS, 1);
        op.setLockedBy("worker");
        LightningPayment payment = lightningPayment(LightningPaymentStatus.IN_FLIGHT);
        when(lightningNodePort.findPayment(PAYMENT_HASH)).thenReturn(Optional.of(payment));

        service.executeLightning(op);

        assertEquals(NodeOperationStatus.PENDING, op.getStatus());
        assertEquals("LND payment is IN_FLIGHT", op.getLastError());
        assertNull(op.getLockedBy());
        assertNotNull(op.getNextAttemptAt());
        verify(lightningNodePort, never()).executeLightningPayment(any(), anyInt(), anyInt());
        verifyNoInteractions(transactionsPort);
        verify(nodeOperationsRepository).save(op);
    }

    @Test
    void lightning_existingFailedPayment_failsTransactionAndOperation() {
        NodeOperationEntity op = buildLightningOperation(NodeOperationStatus.IN_PROGRESS, 1);
        LightningPayment payment = new LightningPayment(
                LightningPaymentStatus.FAILED,
                "FAILURE_REASON_NO_ROUTE",
                0,
                0
        );
        when(lightningNodePort.findPayment(PAYMENT_HASH)).thenReturn(Optional.of(payment));

        service.executeLightning(op);

        assertEquals(NodeOperationStatus.FAILED, op.getStatus());
        assertEquals("LND reported payment failure: FAILURE_REASON_NO_ROUTE", op.getLastError());
        assertNotNull(op.getCompletedAt());
        verify(transactionsPort).failTransaction(TX_ID, "FAILURE_REASON_NO_ROUTE");
        verify(lightningNodePort, never()).executeLightningPayment(any(), anyInt(), anyInt());
        verify(nodeOperationsRepository).save(op);
    }

    @Test
    void lightning_noExistingPayment_sendSuccess_settlesThroughTransactionsPort() {
        NodeOperationEntity op = buildLightningOperation(NodeOperationStatus.IN_PROGRESS, 1);
        LightningPayment payment = new LightningPayment(LightningPaymentStatus.SUCCEEDED, "FAILURE_REASON_NONE", 0, 1_250);
        when(lightningNodePort.findPayment(PAYMENT_HASH)).thenReturn(Optional.empty());
        when(lightningNodePort.executeLightningPayment(any(), eq(200), eq(200))).thenReturn(Optional.of(payment));

        service.executeLightning(op);

        assertEquals(NodeOperationStatus.SUCCEEDED, op.getStatus());
        verify(transactionsPort).addFeeToTransaction(TX_ID, 2);
        verify(transactionsPort).confirmTransaction(TX_ID, USER_ID);
        verify(nodeOperationsRepository).save(op);
    }

    @Test
    void lightning_maxAttemptsWithoutExistingPayment_failsTransactionAndOperation() {
        NodeOperationEntity op = buildLightningOperation(NodeOperationStatus.IN_PROGRESS, 6);
        when(lightningNodePort.findPayment(PAYMENT_HASH)).thenReturn(Optional.empty());

        service.executeLightning(op);

        assertEquals(NodeOperationStatus.FAILED, op.getStatus());
        assertEquals("Max lightning attempts (5) reached", op.getLastError());
        verify(transactionsPort).failTransaction(TX_ID, "Max attempts reached");
        verify(lightningNodePort, never()).executeLightningPayment(any(), anyInt(), anyInt());
        verify(nodeOperationsRepository).save(op);
    }

    @Test
    void lightning_transportErrorBeforeMaxAttempts_recordsRetryWithoutSettlement() {
        NodeOperationEntity op = buildLightningOperation(NodeOperationStatus.IN_PROGRESS, 2);
        LightningNodeTransportException transport = transportException();
        when(lightningNodePort.findPayment(PAYMENT_HASH)).thenReturn(Optional.empty());
        when(lightningNodePort.executeLightningPayment(any(), eq(200), eq(200))).thenThrow(transport);

        service.executeLightning(op);

        assertEquals(NodeOperationStatus.PENDING, op.getStatus());
        assertTrue(op.getLastError().contains("Transport error"));
        assertNull(op.getLockedBy());
        verify(transactionsPort, never()).confirmTransaction(any(), any());
        verify(transactionsPort, never()).failTransaction(any(), any());
        verify(nodeOperationsRepository).save(op);
    }

    @Test
    void lightning_transportErrorInspectingExistingPayment_recordsRetryWithoutSending() {
        NodeOperationEntity op = buildLightningOperation(NodeOperationStatus.IN_PROGRESS, 2);
        LightningNodeTransportException transport = transportException();
        when(lightningNodePort.findPayment(PAYMENT_HASH)).thenThrow(transport);

        service.executeLightning(op);

        assertEquals(NodeOperationStatus.PENDING, op.getStatus());
        assertTrue(op.getLastError().contains("inspecting payment state"));
        verify(lightningNodePort, never()).executeLightningPayment(any(), anyInt(), anyInt());
        verify(transactionsPort, never()).confirmTransaction(any(), any());
        verify(transactionsPort, never()).failTransaction(any(), any());
        verify(nodeOperationsRepository).save(op);
    }

    @Test
    void lightning_transportErrorAtMaxAttempts_recordsUnknownOutcome() {
        NodeOperationEntity op = buildLightningOperation(NodeOperationStatus.IN_PROGRESS, 5);
        LightningNodeTransportException transport = transportException();
        when(lightningNodePort.findPayment(PAYMENT_HASH)).thenReturn(Optional.empty());
        when(lightningNodePort.executeLightningPayment(any(), eq(200), eq(200))).thenThrow(transport);

        service.executeLightning(op);

        assertEquals(NodeOperationStatus.UNKNOWN_OUTCOME, op.getStatus());
        assertTrue(op.getLastError().contains("after max attempts"));
        assertNotNull(op.getCompletedAt());
        NodeOperationUnknownOutcomeFacts facts = captureUnknownOutcomeFacts();
        assertEquals(op.getId().toString(), facts.operationId());
        assertEquals(TX_ID, facts.transactionId());
        assertEquals(USER_ID, facts.userId());
        assertEquals(NodeOperationType.LIGHTNING_PAYMENT.name(), facts.operationType());
        assertEquals(NodeOperationStatus.UNKNOWN_OUTCOME.name(), facts.operationStatus());
        assertEquals(PAYMENT_HASH, facts.referenceId());
        assertEquals(5, facts.attemptCount());
        assertTrue(facts.operationError().contains("after max attempts"));
        verify(transactionsPort, never()).failTransaction(any(), any());
        verify(nodeOperationsRepository).save(op);
    }

    @Test
    void lightning_unknownSendOutcomeBeforeMaxAttempts_recordsRetryWithoutSettlement() {
        NodeOperationEntity op = buildLightningOperation(NodeOperationStatus.IN_PROGRESS, 2);
        LightningPayment payment = lightningPayment(LightningPaymentStatus.INITIATED);
        when(lightningNodePort.findPayment(PAYMENT_HASH)).thenReturn(Optional.empty());
        when(lightningNodePort.executeLightningPayment(any(), eq(200), eq(200))).thenReturn(Optional.of(payment));

        service.executeLightning(op);

        assertEquals(NodeOperationStatus.PENDING, op.getStatus());
        assertEquals("LND payment outcome is unknown: INITIATED", op.getLastError());
        verify(transactionsPort, never()).confirmTransaction(any(), any());
        verify(transactionsPort, never()).failTransaction(any(), any());
        verify(webhookEventService, never()).createNodeOperationUnknownOutcomeEvent(any());
        verify(nodeOperationsRepository).save(op);
    }

    @Test
    void lightning_unknownExistingPaymentAtMaxAttempts_recordsUnknownOutcome() {
        NodeOperationEntity op = buildLightningOperation(NodeOperationStatus.IN_PROGRESS, 5);
        LightningPayment payment = lightningPayment(LightningPaymentStatus.INITIATED);
        when(lightningNodePort.findPayment(PAYMENT_HASH)).thenReturn(Optional.of(payment));

        service.executeLightning(op);

        assertEquals(NodeOperationStatus.UNKNOWN_OUTCOME, op.getStatus());
        assertTrue(op.getLastError().contains("LND payment outcome is unknown: INITIATED"));
        assertTrue(op.getLastError().contains("after max attempts"));
        NodeOperationUnknownOutcomeFacts facts = captureUnknownOutcomeFacts();
        assertEquals(NodeOperationType.LIGHTNING_PAYMENT.name(), facts.operationType());
        assertEquals(PAYMENT_HASH, facts.referenceId());
        verify(lightningNodePort, never()).executeLightningPayment(any(), anyInt(), anyInt());
        verify(transactionsPort, never()).confirmTransaction(any(), any());
        verify(transactionsPort, never()).failTransaction(any(), any());
        verify(nodeOperationsRepository).save(op);
    }

    @Test
    void onChain_txidSaved_confirmationThrows_nextRunResumesWithoutSendOnChain() {
        NodeOperationEntity op = buildOnChainOperation(NodeOperationStatus.IN_PROGRESS, 1);

        when(lightningNodePort.sendOnChain(any())).thenReturn("abc123txid");
        when(transactionsPort.confirmTransaction(TX_ID, USER_ID))
                .thenThrow(new AratiriException("confirmation failed"))
                .thenReturn(mock(com.aratiri.transactions.application.dto.TransactionDTOResponse.class));

        assertThrows(AratiriException.class, () -> service.executeOnChain(op));

        assertEquals(NodeOperationStatus.BROADCASTED, op.getStatus());
        assertEquals("abc123txid", op.getExternalId());
        verify(lightningNodePort).sendOnChain(any());
        verify(transactionsPort, times(1)).confirmTransaction(TX_ID, USER_ID);
        verify(transactionsPort, never()).failTransaction(any(), any());

        NodeOperationEntity resumedOp = buildOnChainOperation(NodeOperationStatus.IN_PROGRESS, 2);
        resumedOp.setExternalId("abc123txid");

        service.executeOnChain(resumedOp);

        verifyNoMoreInteractions(lightningNodePort);
        verify(transactionsPort, times(2)).confirmTransaction(TX_ID, USER_ID);
    }

    @Test
    void enqueueOnChainSend_storesDurablePayloadFromOperationFactAndIsIdempotentByTransactionId() {
        OnChainSendOperationFact fact = onChainFact();
        when(nodeOperationsRepository.findByTransactionId(TX_ID)).thenReturn(Optional.empty());
        when(nodeOperationsRepository.save(any(NodeOperationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NodeOperationEntity saved = service.enqueueOnChainSend(fact);

        assertEquals(TX_ID, saved.getTransactionId());
        assertEquals(USER_ID, saved.getUserId());
        assertEquals(NodeOperationType.ONCHAIN_SEND, saved.getOperationType());
        assertEquals(NodeOperationStatus.PENDING, saved.getStatus());
        assertEquals(0, saved.getAttemptCount());
        assertNotNull(saved.getNextAttemptAt());
        assertTrue(saved.getRequestPayload().contains("\"address\":\"tb1qtest\""));
        assertTrue(saved.getRequestPayload().contains("\"sats_amount\":1000"));
        assertTrue(saved.getRequestPayload().contains("\"sat_per_vbyte\":12"));
        assertTrue(saved.getRequestPayload().contains("\"target_conf\":3"));
        assertTrue(saved.getRequestPayload().contains("\"external_reference\":\"external-1\""));
        assertTrue(saved.getRequestPayload().contains("\"metadata\":\"{\\\"order\\\":\\\"123\\\"}\""));

        NodeOperationEntity existing = NodeOperationEntity.builder()
                .transactionId(TX_ID)
                .userId(USER_ID)
                .operationType(NodeOperationType.ONCHAIN_SEND)
                .status(NodeOperationStatus.PENDING)
                .requestPayload(saved.getRequestPayload())
                .attemptCount(0)
                .build();
        when(nodeOperationsRepository.findByTransactionId(TX_ID)).thenReturn(Optional.of(existing));

        assertSame(existing, service.enqueueOnChainSend(fact));
        verify(nodeOperationsRepository, times(1)).save(any(NodeOperationEntity.class));
    }

    @Test
    void onChain_sendOnChainFailure_marksUnknownOutcome() {
        NodeOperationEntity op = buildOnChainOperation(NodeOperationStatus.IN_PROGRESS, 1);

        when(lightningNodePort.sendOnChain(any())).thenThrow(new RuntimeException("node unreachable"));
        when(transactionsPort.getTransactionById(TX_ID, USER_ID)).thenReturn(Optional.of(transaction()));

        service.executeOnChain(op);

        assertEquals(NodeOperationStatus.UNKNOWN_OUTCOME, op.getStatus());
        assertEquals("Exception sending on-chain transaction: node unreachable", op.getLastError());
        assertNull(op.getExternalId());
        verify(nodeOperationsRepository).save(op);
        NodeOperationUnknownOutcomeFacts facts = captureUnknownOutcomeFacts();
        assertEquals(op.getId().toString(), facts.operationId());
        assertEquals(TX_ID, facts.transactionId());
        assertEquals(USER_ID, facts.userId());
        assertEquals(NodeOperationType.ONCHAIN_SEND.name(), facts.operationType());
        assertEquals(NodeOperationStatus.UNKNOWN_OUTCOME.name(), facts.operationStatus());
        assertEquals(1, facts.attemptCount());
        assertEquals("external-1", facts.externalReference());
        assertEquals("{\"order\":\"123\"}", facts.metadata());
        assertEquals(1_000L, facts.amountSat());
        assertEquals(TransactionStatus.PENDING.name(), facts.transactionStatus());
        assertEquals("transaction-ref-1", facts.referenceId());
        assertTrue(facts.operationError().contains("node unreachable"));
        verify(transactionsPort, never()).confirmTransaction(any(), any());
    }

    @Test
    void onChain_maxAttemptsWithoutBroadcastId_recordsUnknownOutcomeWithoutSendingAgain() {
        NodeOperationEntity op = buildOnChainOperation(NodeOperationStatus.IN_PROGRESS, 6);
        when(transactionsPort.getTransactionById(TX_ID, USER_ID)).thenReturn(Optional.of(transaction()));

        service.executeOnChain(op);

        assertEquals(NodeOperationStatus.UNKNOWN_OUTCOME, op.getStatus());
        assertEquals("Max on-chain attempts (5) reached without a recorded broadcast transaction id", op.getLastError());
        assertNull(op.getExternalId());
        NodeOperationUnknownOutcomeFacts facts = captureUnknownOutcomeFacts();
        assertEquals(NodeOperationType.ONCHAIN_SEND.name(), facts.operationType());
        assertEquals("external-1", facts.externalReference());
        assertEquals("transaction-ref-1", facts.referenceId());
        verify(lightningNodePort, never()).sendOnChain(any());
        verify(transactionsPort, never()).confirmTransaction(any(), any());
        verify(transactionsPort, never()).failTransaction(any(), any());
    }

    @Test
    void onChain_blankTxid_marksUnknownOutcomeWithoutConfirmingTransaction() {
        NodeOperationEntity op = buildOnChainOperation(NodeOperationStatus.IN_PROGRESS, 1);
        when(lightningNodePort.sendOnChain(any())).thenReturn(" ");
        when(transactionsPort.getTransactionById(TX_ID, USER_ID)).thenReturn(Optional.of(transaction()));

        service.executeOnChain(op);

        assertEquals(NodeOperationStatus.UNKNOWN_OUTCOME, op.getStatus());
        assertEquals("LND sendCoins returned no transaction id", op.getLastError());
        assertNull(op.getExternalId());
        NodeOperationUnknownOutcomeFacts facts = captureUnknownOutcomeFacts();
        assertEquals(NodeOperationType.ONCHAIN_SEND.name(), facts.operationType());
        assertEquals(TransactionStatus.PENDING.name(), facts.transactionStatus());
        verify(transactionsPort, never()).confirmTransaction(any(), any());
    }

    @Test
    void onChain_withExternalId_confirmsWithoutSendOnChain() {
        NodeOperationEntity op = buildOnChainOperation(NodeOperationStatus.IN_PROGRESS, 1);
        op.setExternalId("preexisting-txid");

        service.executeOnChain(op);

        verify(lightningNodePort, never()).sendOnChain(any());
        verify(transactionsPort).confirmTransaction(TX_ID, USER_ID);
    }

    private NodeOperationUnknownOutcomeFacts captureUnknownOutcomeFacts() {
        ArgumentCaptor<NodeOperationUnknownOutcomeFacts> captor = ArgumentCaptor.forClass(NodeOperationUnknownOutcomeFacts.class);
        verify(webhookEventService).createNodeOperationUnknownOutcomeEvent(captor.capture());
        return captor.getValue();
    }

    private NodeOperationEntity buildLightningOperation(NodeOperationStatus status, int attemptCount) {
        NodeOperationEntity op = new NodeOperationEntity();
        op.setId(UUID.randomUUID());
        op.setTransactionId(TX_ID);
        op.setUserId(USER_ID);
        op.setOperationType(NodeOperationType.LIGHTNING_PAYMENT);
        op.setStatus(status);
        op.setAttemptCount(attemptCount);
        op.setReferenceId(PAYMENT_HASH);
        op.setRequestPayload(JsonUtils.toJson(lightningPayment().withPaymentHash(PAYMENT_HASH)));
        return op;
    }

    private LightningPaymentOperation lightningPayment() {
        PayInvoiceRequestDTO request = new PayInvoiceRequestDTO();
        request.setInvoice("lightning:lnbc1test");
        request.setFeeLimitSat(101L);
        request.setTimeoutSeconds(22);
        request.setExternalReference("external-1");
        request.setMetadata("{\"order\":\"123\"}");
        return LightningPaymentOperation.fromPaymentRequest(TX_ID, USER_ID, request);
    }

    private LightningPayment lightningPayment(LightningPaymentStatus status) {
        return new LightningPayment(status, "FAILURE_REASON_NONE", 0, 0);
    }

    private LightningNodeTransportException transportException() {
        return new LightningNodeTransportException("node down", new RuntimeException("unavailable"));
    }

    private NodeOperationEntity buildOnChainOperation(NodeOperationStatus status, int attemptCount) {
        when(nodeOperationsRepository.findByTransactionId(TX_ID)).thenReturn(Optional.empty());
        when(nodeOperationsRepository.save(any(NodeOperationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        NodeOperationEntity op = service.enqueueOnChainSend(onChainFact());
        clearInvocations(nodeOperationsRepository);
        op.setId(UUID.randomUUID());
        op.setStatus(status);
        op.setAttemptCount(attemptCount);
        return op;
    }

    private OnChainSendOperationFact onChainFact() {
        return new OnChainSendOperationFact(
                TX_ID,
                USER_ID,
                "tb1qtest",
                1_000L,
                12L,
                3,
                "external-1",
                "{\"order\":\"123\"}"
        );
    }

    private TransactionDTOResponse transaction() {
        return TransactionDTOResponse.builder()
                .id(TX_ID)
                .userId(USER_ID)
                .amountSat(1_000L)
                .status(TransactionStatus.PENDING)
                .referenceId("transaction-ref-1")
                .externalReference("external-1")
                .metadata("{\"order\":\"123\"}")
                .build();
    }
}
