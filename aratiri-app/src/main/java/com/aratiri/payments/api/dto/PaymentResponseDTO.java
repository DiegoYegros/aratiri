package com.aratiri.payments.api.dto;

import com.aratiri.transactions.application.dto.TransactionStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentResponseDTO {
    private String transactionId;
    private TransactionStatus status;
    private String message;
}