package com.aratiri.infrastructure.persistence.jpa.entity;

public enum OutboxPublishStatus {
    PENDING,
    FAILED,
    PUBLISHED,
    INVALID
}
