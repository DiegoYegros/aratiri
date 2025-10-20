package com.aratiri.transactions.application.dto;

import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class CreateTransactionRequest {
    private String userId;
    private BigDecimal amount;
    private TransactionCurrency currency;
    private TransactionType type;
    private TransactionStatus status;
    private String description;
    private String referenceId;
}

