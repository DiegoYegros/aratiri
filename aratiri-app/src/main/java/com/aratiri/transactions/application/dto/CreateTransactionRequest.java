package com.aratiri.transactions.application.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class CreateTransactionRequest {
    private String userId;
    private long amountSat;
    private TransactionCurrency currency;
    private TransactionType type;
    private TransactionStatus status;
    private String description;
    private String referenceId;
}