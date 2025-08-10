package com.aratiri.aratiri.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InternalTransferInitiatedEvent {
    private String transactionId;
    private String senderId;
    private String receiverId;
    private long amountSat;
    private String paymentHash;
}