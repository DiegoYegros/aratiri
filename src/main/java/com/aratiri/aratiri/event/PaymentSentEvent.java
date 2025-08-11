package com.aratiri.aratiri.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentSentEvent {
    private String userId;
    private String transactionId;
    private Long amount;
    private String paymentHash;
    private LocalDateTime timestamp;
    private String memo;
}