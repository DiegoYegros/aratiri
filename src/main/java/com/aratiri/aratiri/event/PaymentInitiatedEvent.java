package com.aratiri.aratiri.event;

import com.aratiri.aratiri.dto.payments.PayInvoiceRequestDTO;
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