package com.aratiri.transactions.application.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@Builder
public class TransactionDTOResponse {
    private String id;
    private String userId;
    private long amountSat;
    private Long balanceAfterSat;
    private TransactionCurrency currency;
    private TransactionType type;
    private TransactionStatus status;
    private String description;
    private String failureReason;
    private String referenceId;
    private String externalReference;
    private String metadata;
    private OffsetDateTime createdAt;
}