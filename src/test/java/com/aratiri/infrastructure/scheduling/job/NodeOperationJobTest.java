package com.aratiri.infrastructure.scheduling.job;

import com.aratiri.infrastructure.configuration.NodeOperationProperties;
import com.aratiri.infrastructure.persistence.jpa.entity.NodeOperationEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.NodeOperationStatus;
import com.aratiri.infrastructure.persistence.jpa.entity.NodeOperationType;
import com.aratiri.infrastructure.persistence.jpa.repository.NodeOperationsRepository;
import com.aratiri.payments.application.port.out.LightningNodePort;
import com.aratiri.shared.exception.AratiriException;
import com.aratiri.transactions.application.port.in.TransactionsPort;
import com.aratiri.webhooks.application.WebhookEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NodeOperationJobTest {

    @Mock
    private NodeOperationsRepository nodeOperationsRepository;

    @Mock
    private NodeOperationProperties nodeOperationProperties;

    @Mock
    private LightningNodePort lightningNodePort;

    @Mock
    private TransactionsPort transactionsPort;

    @Mock
    private NodeOperationClaimer claimer;

    @Mock
    private WebhookEventService webhookEventService;

    private NodeOperationJob job;
    private NodeOperationState stateManager;

    private static final String TX_ID = "tx-123";
    private static final String USER_ID = "user-456";

    @BeforeEach
    void setUp() {
        when(nodeOperationProperties.getOnchainMaxAttempts()).thenReturn(5);
        stateManager = new NodeOperationState(nodeOperationsRepository, transactionsPort);
        job = new NodeOperationJob(nodeOperationsRepository, nodeOperationProperties, lightningNodePort, transactionsPort, stateManager, claimer, webhookEventService);
    }

    @Test
    void onChain_txidSaved_confirmationThrows_nextRunResumesWithoutSendOnChain() {
        NodeOperationEntity op = buildOnChainOperation(NodeOperationStatus.IN_PROGRESS, 1);

        when(lightningNodePort.sendOnChain(any())).thenReturn("abc123txid");
        when(transactionsPort.confirmTransaction(TX_ID, USER_ID))
                .thenThrow(new AratiriException("confirmation failed"))
                .thenReturn(mock(com.aratiri.transactions.application.dto.TransactionDTOResponse.class));

        assertThrows(AratiriException.class, () -> job.executeOnChain(op));

        assertEquals(NodeOperationStatus.BROADCASTED, op.getStatus());
        assertEquals("abc123txid", op.getExternalId());
        verify(lightningNodePort).sendOnChain(any());
        verify(transactionsPort, times(1)).confirmTransaction(TX_ID, USER_ID);
        verify(transactionsPort, never()).failTransaction(any(), any());

        NodeOperationEntity resumedOp = buildOnChainOperation(NodeOperationStatus.IN_PROGRESS, 2);
        resumedOp.setExternalId("abc123txid");
        resumedOp.setStatus(NodeOperationStatus.IN_PROGRESS);

        job.executeOnChain(resumedOp);

        verifyNoMoreInteractions(lightningNodePort);
        verify(transactionsPort, times(2)).confirmTransaction(TX_ID, USER_ID);
    }

    @Test
    void onChain_sendOnChainFailure_marksUnknownOutcome() {
        NodeOperationEntity op = buildOnChainOperation(NodeOperationStatus.IN_PROGRESS, 1);

        when(lightningNodePort.sendOnChain(any())).thenThrow(new RuntimeException("node unreachable"));

        job.executeOnChain(op);

        assertEquals(NodeOperationStatus.UNKNOWN_OUTCOME, op.getStatus());
        assertEquals("node unreachable", op.getLastError());
        assertNull(op.getExternalId());
        verify(nodeOperationsRepository).save(op);
        verify(transactionsPort, never()).confirmTransaction(any(), any());
    }

    @Test
    void onChain_withExternalId_confirmsWithoutSendOnChain() {
        NodeOperationEntity op = buildOnChainOperation(NodeOperationStatus.IN_PROGRESS, 1);
        op.setExternalId("preexisting-txid");

        job.executeOnChain(op);

        verify(lightningNodePort, never()).sendOnChain(any());
        verify(transactionsPort).confirmTransaction(TX_ID, USER_ID);
    }

    private NodeOperationEntity buildOnChainOperation(NodeOperationStatus status, int attemptCount) {
        NodeOperationEntity op = new NodeOperationEntity();
        op.setId(UUID.randomUUID());
        op.setTransactionId(TX_ID);
        op.setUserId(USER_ID);
        op.setOperationType(NodeOperationType.ONCHAIN_SEND);
        op.setStatus(status);
        op.setAttemptCount(attemptCount);
        op.setRequestPayload("{\"address\":\"tb1qtest\",\"sats_amount\":1000}");
        return op;
    }
}
