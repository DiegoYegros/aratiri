package com.aratiri.infrastructure.nodeoperations;

import com.aratiri.infrastructure.configuration.NodeOperationProperties;
import com.aratiri.infrastructure.persistence.jpa.entity.NodeOperationEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.NodeOperationStatus;
import com.aratiri.infrastructure.persistence.jpa.repository.NodeOperationsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class NodeOperationClaimer {

    private static final String LOCKED_BY_PREFIX = "worker-";

    private final NodeOperationsRepository nodeOperationsRepository;
    private final NodeOperationProperties nodeOperationProperties;

    @Transactional
    List<NodeOperationEntity> claimPendingBatch() {
        Instant now = Instant.now();
        Instant lockedUntil = now.plusSeconds(nodeOperationProperties.getLeaseSeconds());
        String lockedBy = LOCKED_BY_PREFIX + Thread.currentThread().threadId();
        PageRequest page = PageRequest.ofSize(nodeOperationProperties.getBatchSize());

        List<NodeOperationEntity> pendingOps = nodeOperationsRepository.findPendingDueOperations(
                NodeOperationStatus.PENDING, now, page
        );

        List<NodeOperationEntity> claimed = new ArrayList<>();
        for (NodeOperationEntity op : pendingOps) {
            int updated = nodeOperationsRepository.claimPendingOperation(
                    op.getId(), NodeOperationStatus.IN_PROGRESS, lockedBy, lockedUntil, now, NodeOperationStatus.PENDING
            );
            if (updated > 0) {
                claimed.add(refresh(op.getId()));
            }
        }
        return claimed;
    }

    @Transactional
    List<NodeOperationEntity> claimStaleBatch() {
        Instant now = Instant.now();
        Instant lockedUntil = now.plusSeconds(nodeOperationProperties.getLeaseSeconds());
        String lockedBy = LOCKED_BY_PREFIX + Thread.currentThread().threadId();
        PageRequest page = PageRequest.ofSize(nodeOperationProperties.getBatchSize());

        List<NodeOperationEntity> staleOps = nodeOperationsRepository.findStaleInProgressOperations(
                NodeOperationStatus.IN_PROGRESS, now, page
        );

        List<NodeOperationEntity> claimed = new ArrayList<>();
        for (NodeOperationEntity op : staleOps) {
            int updated = nodeOperationsRepository.claimStaleOperation(
                    op.getId(), NodeOperationStatus.IN_PROGRESS, lockedBy, lockedUntil, now, NodeOperationStatus.IN_PROGRESS
            );
            if (updated > 0) {
                claimed.add(refresh(op.getId()));
            }
        }
        return claimed;
    }

    @Transactional
    List<NodeOperationEntity> claimBroadcastedBatch() {
        Instant now = Instant.now();
        Instant lockedUntil = now.plusSeconds(nodeOperationProperties.getLeaseSeconds());
        String lockedBy = LOCKED_BY_PREFIX + Thread.currentThread().threadId();
        PageRequest page = PageRequest.ofSize(nodeOperationProperties.getBatchSize());

        List<NodeOperationEntity> broadcastedOps = nodeOperationsRepository.findBroadcastedOperations(
                NodeOperationStatus.BROADCASTED, page
        );

        List<NodeOperationEntity> claimed = new ArrayList<>();
        for (NodeOperationEntity op : broadcastedOps) {
            int updated = nodeOperationsRepository.claimBroadcastedOperation(
                    op.getId(), NodeOperationStatus.IN_PROGRESS, lockedBy, lockedUntil, now, NodeOperationStatus.BROADCASTED
            );
            if (updated > 0) {
                claimed.add(refresh(op.getId()));
            }
        }
        return claimed;
    }

    private NodeOperationEntity refresh(UUID id) {
        return nodeOperationsRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Claimed operation not found: " + id));
    }
}
