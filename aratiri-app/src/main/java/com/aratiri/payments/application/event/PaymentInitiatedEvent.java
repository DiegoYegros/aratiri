package com.aratiri.payments.application.event;

import com.aratiri.payments.application.dto.PayInvoiceRequestDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentInitiatedEvent {
    private String userId;
    private String transactionId;
    private PayInvoiceRequestDTO payRequest;
}