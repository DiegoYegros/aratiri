package com.aratiri.aratiri.event;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class InvoiceSettledEvent {
    private String userId;
    private Long amount;
    private String paymentHash;
    private LocalDateTime timestamp;
}