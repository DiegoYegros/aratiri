package com.aratiri.payments.application.dto;

import com.aratiri.transactions.application.dto.TransactionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponseDTO {
    private String transactionId;
    private TransactionStatus status;
    private String message;
}