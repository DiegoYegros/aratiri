package com.aratiri.aratiri.dto.transactions;

import com.aratiri.aratiri.enums.TransactionCurrency;
import com.aratiri.aratiri.enums.TransactionStatus;
import com.aratiri.aratiri.enums.TransactionType;
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

