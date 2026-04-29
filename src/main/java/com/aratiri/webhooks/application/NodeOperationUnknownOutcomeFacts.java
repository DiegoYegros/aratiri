package com.aratiri.webhooks.application;

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
}
