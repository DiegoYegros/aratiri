package com.aratiri.aratiri.dto.transactions;

import com.aratiri.aratiri.enums.TransactionCurrency;
import com.aratiri.aratiri.enums.TransactionStatus;
import com.aratiri.aratiri.enums.TransactionType;
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
    private String referenceId;
    private OffsetDateTime createdAt;
}