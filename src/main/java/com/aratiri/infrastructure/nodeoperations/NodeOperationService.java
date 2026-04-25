package com.aratiri.infrastructure.nodeoperations;

import com.aratiri.infrastructure.persistence.jpa.entity.NodeOperationEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.NodeOperationStatus;
import com.aratiri.infrastructure.persistence.jpa.entity.NodeOperationType;
import com.aratiri.infrastructure.persistence.jpa.repository.NodeOperationsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class NodeOperationService {

    private final NodeOperationsRepository nodeOperationsRepository;

    @Transactional
    public NodeOperationEntity enqueueLightningPayment(String transactionId, String userId, String paymentHash, String requestPayload) {
        Optional<NodeOperationEntity> existing = nodeOperationsRepository.findByTransactionId(transactionId);
        if (existing.isPresent()) {
            log.info("Node operation already exists for transactionId: {}", transactionId);
            return existing.get();
        }

        NodeOperationEntity operation = NodeOperationEntity.builder()
                .transactionId(transactionId)
                .userId(userId)
                .operationType(NodeOperationType.LIGHTNING_PAYMENT)
                .status(NodeOperationStatus.PENDING)
                .referenceId(paymentHash)
                .requestPayload(requestPayload)
                .attemptCount(0)
                .nextAttemptAt(Instant.now())
                .build();

        NodeOperationEntity saved = nodeOperationsRepository.save(operation);
        log.info("Enqueued lightning payment operation: id={}, transactionId={}", saved.getId(), transactionId);
        return saved;
    }

    @Transactional
    public NodeOperationEntity enqueueOnChainSend(String transactionId, String userId, String requestPayload) {
        Optional<NodeOperationEntity> existing = nodeOperationsRepository.findByTransactionId(transactionId);
        if (existing.isPresent()) {
            log.info("Node operation already exists for transactionId: {}", transactionId);
            return existing.get();
        }

        NodeOperationEntity operation = NodeOperationEntity.builder()
                .transactionId(transactionId)
                .userId(userId)
                .operationType(NodeOperationType.ONCHAIN_SEND)
                .status(NodeOperationStatus.PENDING)
                .requestPayload(requestPayload)
                .attemptCount(0)
                .nextAttemptAt(Instant.now())
                .build();

        NodeOperationEntity saved = nodeOperationsRepository.save(operation);
        log.info("Enqueued on-chain send operation: id={}, transactionId={}", saved.getId(), transactionId);
        return saved;
    }
}
