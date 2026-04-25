package com.aratiri.admin.application.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
public class NodeOperationResponseDTO {
    private String id;
    private String transactionId;
    private String userId;
    private String operationType;
    private String status;
    private String referenceId;
    private String externalId;
    private Integer attemptCount;
    private String lastError;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime completedAt;
}
