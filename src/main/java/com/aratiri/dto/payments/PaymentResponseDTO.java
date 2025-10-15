package com.aratiri.dto.payments;

import com.aratiri.dto.transactions.TransactionStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentResponseDTO {
    private String transactionId;
    private TransactionStatus status;
    private String message;
}