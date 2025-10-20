package com.aratiri.payments.application.event;

import com.aratiri.payments.api.dto.OnChainPaymentDTOs;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OnChainPaymentInitiatedEvent {
    private String userId;
    private String transactionId;
    private OnChainPaymentDTOs.SendOnChainRequestDTO paymentRequest;
}
