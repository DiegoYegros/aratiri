package com.aratiri.infrastructure.nodeoperations;

import com.aratiri.infrastructure.configuration.NodeOperationProperties;
import com.aratiri.infrastructure.persistence.jpa.entity.NodeOperationEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.NodeOperationStatus;
import com.aratiri.infrastructure.persistence.jpa.repository.NodeOperationsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NodeOperationClaimerTest {

    @Mock
    private NodeOperationsRepository nodeOperationsRepository;

    private NodeOperationProperties properties;
    private NodeOperationClaimer claimer;

    @BeforeEach
    void setUp() {
        properties = new NodeOperationProperties();
        properties.setBatchSize(5);
        properties.setLeaseSeconds(300);
        claimer = new NodeOperationClaimer(nodeOperationsRepository, properties);
    }

    @Test
    void claimPendingBatch_claimsOperations() {
        UUID opId = UUID.randomUUID();
        NodeOperationEntity op = NodeOperationEntity.builder()
                .id(opId)
                .status(NodeOperationStatus.PENDING)
                .build();

        when(nodeOperationsRepository.findPendingDueOperations(
                eq(NodeOperationStatus.PENDING), any(Instant.class), any(PageRequest.class)))
                .thenReturn(List.of(op));
        when(nodeOperationsRepository.claimPendingOperation(
                eq(opId), eq(NodeOperationStatus.IN_PROGRESS), anyString(), any(Instant.class), any(Instant.class), eq(NodeOperationStatus.PENDING)))
                .thenReturn(1);
        when(nodeOperationsRepository.findById(opId)).thenReturn(Optional.of(op));

        List<NodeOperationEntity> claimed = claimer.claimPendingBatch();

        assertEquals(1, claimed.size());
        verify(nodeOperationsRepository).findPendingDueOperations(
                eq(NodeOperationStatus.PENDING), any(Instant.class), eq(PageRequest.ofSize(5)));
    }

    @Test
    void claimPendingBatch_skipsUnclaimed() {
        UUID opId = UUID.randomUUID();
        NodeOperationEntity op = NodeOperationEntity.builder()
                .id(opId)
                .status(NodeOperationStatus.PENDING)
                .build();

        when(nodeOperationsRepository.findPendingDueOperations(any(), any(), any()))
                .thenReturn(List.of(op));
        when(nodeOperationsRepository.claimPendingOperation(
                eq(opId), any(), anyString(), any(), any(), any()))
                .thenReturn(0);

        List<NodeOperationEntity> claimed = claimer.claimPendingBatch();

        assertTrue(claimed.isEmpty());
        verify(nodeOperationsRepository, never()).findById(any());
    }

    @Test
    void claimPendingBatch_emptyWhenNoPending() {
        when(nodeOperationsRepository.findPendingDueOperations(any(), any(), any()))
                .thenReturn(List.of());

        List<NodeOperationEntity> claimed = claimer.claimPendingBatch();

        assertTrue(claimed.isEmpty());
    }

    @Test
    void claimStaleBatch_claimsStaleOperations() {
        UUID opId = UUID.randomUUID();
        NodeOperationEntity op = NodeOperationEntity.builder()
                .id(opId)
                .status(NodeOperationStatus.IN_PROGRESS)
                .build();

        when(nodeOperationsRepository.findStaleInProgressOperations(
                eq(NodeOperationStatus.IN_PROGRESS), any(Instant.class), any(PageRequest.class)))
                .thenReturn(List.of(op));
        when(nodeOperationsRepository.claimStaleOperation(
                eq(opId), eq(NodeOperationStatus.IN_PROGRESS), anyString(), any(Instant.class), any(Instant.class), eq(NodeOperationStatus.IN_PROGRESS)))
                .thenReturn(1);
        when(nodeOperationsRepository.findById(opId)).thenReturn(Optional.of(op));

        List<NodeOperationEntity> claimed = claimer.claimStaleBatch();

        assertEquals(1, claimed.size());
        verify(nodeOperationsRepository).findStaleInProgressOperations(
                eq(NodeOperationStatus.IN_PROGRESS), any(Instant.class), eq(PageRequest.ofSize(5)));
    }

    @Test
    void claimStaleBatch_emptyWhenNoStale() {
        when(nodeOperationsRepository.findStaleInProgressOperations(any(), any(), any()))
                .thenReturn(List.of());

        List<NodeOperationEntity> claimed = claimer.claimStaleBatch();

        assertTrue(claimed.isEmpty());
    }

    @Test
    void claimBroadcastedBatch_claimsBroadcastedOperations() {
        UUID opId = UUID.randomUUID();
        NodeOperationEntity op = NodeOperationEntity.builder()
                .id(opId)
                .status(NodeOperationStatus.BROADCASTED)
                .build();

        when(nodeOperationsRepository.findBroadcastedOperations(
                eq(NodeOperationStatus.BROADCASTED), any(PageRequest.class)))
                .thenReturn(List.of(op));
        when(nodeOperationsRepository.claimBroadcastedOperation(
                eq(opId), eq(NodeOperationStatus.IN_PROGRESS), anyString(), any(Instant.class), any(Instant.class), eq(NodeOperationStatus.BROADCASTED)))
                .thenReturn(1);
        when(nodeOperationsRepository.findById(opId)).thenReturn(Optional.of(op));

        List<NodeOperationEntity> claimed = claimer.claimBroadcastedBatch();

        assertEquals(1, claimed.size());
        verify(nodeOperationsRepository).findBroadcastedOperations(
                NodeOperationStatus.BROADCASTED, PageRequest.ofSize(5));
    }

    @Test
    void claimBroadcastedBatch_emptyWhenNoBroadcasted() {
        when(nodeOperationsRepository.findBroadcastedOperations(any(), any()))
                .thenReturn(List.of());

        List<NodeOperationEntity> claimed = claimer.claimBroadcastedBatch();

        assertTrue(claimed.isEmpty());
    }

    @Test
    void refresh_throwsWhenNotFound() {
        UUID opId = UUID.randomUUID();
        when(nodeOperationsRepository.findPendingDueOperations(any(), any(), any()))
                .thenReturn(List.of(NodeOperationEntity.builder().id(opId).status(NodeOperationStatus.PENDING).build()));
        when(nodeOperationsRepository.claimPendingOperation(any(), any(), anyString(), any(), any(), any()))
                .thenReturn(1);
        when(nodeOperationsRepository.findById(opId)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> claimer.claimPendingBatch());
    }
}
