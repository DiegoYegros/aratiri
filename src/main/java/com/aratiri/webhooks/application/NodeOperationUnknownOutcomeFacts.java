package com.aratiri.webhooks.application;

import com.aratiri.infrastructure.persistence.jpa.entity.NodeOperationEntity;
import com.aratiri.transactions.application.dto.TransactionDTOResponse;

import java.util.Optional;

public record NodeOperationUnknownOutcomeFacts(
        String operationId,
        String transactionId,
        String userId,
        String operationType,
        String operationStatus,
        String referenceId,
        String externalId,
        Integer attemptCount,
        String operationError,
        Long amountSat,
        String transactionStatus,
        String externalReference,
        String metadata
) {
    public static NodeOperationUnknownOutcomeFacts from(
            NodeOperationEntity operation,
            Optional<TransactionDTOResponse> transaction
    ) {
        TransactionDTOResponse tx = transaction.orElse(null);
        return new NodeOperationUnknownOutcomeFacts(
                operation.getId().toString(),
                operation.getTransactionId(),
                operation.getUserId(),
                operation.getOperationType().name(),
                operation.getStatus().name(),
                firstPresent(operation.getReferenceId(), tx == null ? null : tx.getReferenceId()),
                operation.getExternalId(),
                operation.getAttemptCount(),
                operation.getLastError(),
                tx == null ? null : tx.getAmountSat(),
                tx == null || tx.getStatus() == null ? null : tx.getStatus().name(),
                tx == null ? null : tx.getExternalReference(),
                tx == null ? null : tx.getMetadata()
        );
    }

    private static String firstPresent(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }
}
