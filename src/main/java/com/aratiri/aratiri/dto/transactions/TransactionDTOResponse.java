package com.aratiri.aratiri.dto.transactions;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Setter
@Builder
public class TransactionDTOResponse {
    private String id;
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private TransactionCurrency currency;
    private TransactionType type;
    private TransactionStatus status;
    private String description;
    private String failureReason;
    private String referenceId;
    private OffsetDateTime createdAt;
}