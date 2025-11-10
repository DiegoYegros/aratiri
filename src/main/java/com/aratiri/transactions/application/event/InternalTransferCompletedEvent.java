package com.aratiri.transactions.application.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InternalTransferCompletedEvent {
    private String senderId;
    private String receiverId;
    private Long amountSat;
    private String paymentHash;
    private LocalDateTime timestamp;
    private String memo;
}