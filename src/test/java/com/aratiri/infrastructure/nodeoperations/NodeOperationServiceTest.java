package com.aratiri.infrastructure.nodeoperations;

import com.aratiri.infrastructure.configuration.NodeOperationProperties;
import com.aratiri.infrastructure.persistence.jpa.entity.NodeOperationEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.NodeOperationStatus;
import com.aratiri.infrastructure.persistence.jpa.entity.NodeOperationType;
import com.aratiri.infrastructure.persistence.jpa.repository.NodeOperationsRepository;
import com.aratiri.payments.application.dto.OnChainPaymentDTOs;
import com.aratiri.payments.application.dto.PayInvoiceRequestDTO;
import com.aratiri.payments.application.port.out.LightningNodePort;
import com.aratiri.payments.infrastructure.json.JsonUtils;
import com.aratiri.shared.exception.AratiriException;
import com.aratiri.transactions.application.port.in.TransactionsPort;
import com.aratiri.webhooks.application.WebhookEventService;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lnrpc.Payment;
import lnrpc.PaymentFailureReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
        service = new NodeOperationService(
                nodeOperationsRepository,
                nodeOperationProperties,
                lightningNodePort,
                stateManager,
                claimer,
                webhookEventService
        );
        ReflectionTestUtils.setField(service, "defaultFeeLimitSat", 200);
        ReflectionTestUtils.setField(service, "defaultTimeoutSeconds", 200);
    }

    @Test
    void lightning_existingSucceededPayment_settlesAndMarksSucceededWithoutSending() {
        NodeOperationEntity op = buildLightningOperation(NodeOperationStatus.IN_PROGRESS, 1);
        Payment payment = Payment.newBuilder()
                .setStatus(Payment.PaymentStatus.SUCCEEDED)
                .setFeeSat(7)
                .build();
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
        Payment payment = Payment.newBuilder()
                .setStatus(Payment.PaymentStatus.IN_FLIGHT)
                .build();
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
        Payment payment = Payment.newBuilder()
                .setStatus(Payment.PaymentStatus.FAILED)
                .setFailureReason(PaymentFailureReason.FAILURE_REASON_NO_ROUTE)
                .build();
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
        Payment payment = Payment.newBuilder()
                .setStatus(Payment.PaymentStatus.SUCCEEDED)
                .setFeeMsat(1_250)
                .build();
        when(lightningNodePort.findPayment(PAYMENT_HASH)).thenReturn(Optional.empty());
        when(lightningNodePort.executeLightningPayment(any(), eq(200), eq(200))).thenReturn(payment);

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
        StatusRuntimeException transport = new StatusRuntimeException(Status.UNAVAILABLE.withDescription("node down"));
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
        StatusRuntimeException transport = new StatusRuntimeException(Status.UNAVAILABLE.withDescription("node down"));
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
        StatusRuntimeException transport = new StatusRuntimeException(Status.UNAVAILABLE.withDescription("node down"));
        when(lightningNodePort.findPayment(PAYMENT_HASH)).thenReturn(Optional.empty());
        when(lightningNodePort.executeLightningPayment(any(), eq(200), eq(200))).thenThrow(transport);

        service.executeLightning(op);

        assertEquals(NodeOperationStatus.UNKNOWN_OUTCOME, op.getStatus());
        assertTrue(op.getLastError().contains("after max attempts"));
        assertNotNull(op.getCompletedAt());
        verify(webhookEventService).createNodeOperationUnknownOutcomeEvent(op);
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
    void onChain_sendOnChainFailure_marksUnknownOutcome() {
        NodeOperationEntity op = buildOnChainOperation(NodeOperationStatus.IN_PROGRESS, 1);

        when(lightningNodePort.sendOnChain(any())).thenThrow(new RuntimeException("node unreachable"));

        service.executeOnChain(op);

        assertEquals(NodeOperationStatus.UNKNOWN_OUTCOME, op.getStatus());
        assertEquals("node unreachable", op.getLastError());
        assertNull(op.getExternalId());
        verify(nodeOperationsRepository).save(op);
        verify(webhookEventService).createNodeOperationUnknownOutcomeEvent(op);
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

    private NodeOperationEntity buildLightningOperation(NodeOperationStatus status, int attemptCount) {
        PayInvoiceRequestDTO request = new PayInvoiceRequestDTO();
        request.setInvoice("lightning:lnbc1test");
        NodeOperationEntity op = new NodeOperationEntity();
        op.setId(UUID.randomUUID());
        op.setTransactionId(TX_ID);
        op.setUserId(USER_ID);
        op.setOperationType(NodeOperationType.LIGHTNING_PAYMENT);
        op.setStatus(status);
        op.setAttemptCount(attemptCount);
        op.setReferenceId(PAYMENT_HASH);
        op.setRequestPayload(JsonUtils.toJson(request));
        return op;
    }

    private NodeOperationEntity buildOnChainOperation(NodeOperationStatus status, int attemptCount) {
        OnChainPaymentDTOs.SendOnChainRequestDTO request = new OnChainPaymentDTOs.SendOnChainRequestDTO();
        request.setAddress("tb1qtest");
        request.setSatsAmount(1_000L);
        NodeOperationEntity op = new NodeOperationEntity();
        op.setId(UUID.randomUUID());
        op.setTransactionId(TX_ID);
        op.setUserId(USER_ID);
        op.setOperationType(NodeOperationType.ONCHAIN_SEND);
        op.setStatus(status);
        op.setAttemptCount(attemptCount);
        op.setRequestPayload(JsonUtils.toJson(request));
        return op;
    }
}
