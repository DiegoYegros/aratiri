package com.aratiri.infrastructure.persistence.jpa.entity;

public enum NodeOperationStatus {
    PENDING,
    IN_PROGRESS,
    BROADCASTED,
    SUCCEEDED,
    FAILED,
    UNKNOWN_OUTCOME
}
