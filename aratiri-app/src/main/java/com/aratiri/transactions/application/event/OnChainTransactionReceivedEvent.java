package com.aratiri.transactions.application.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OnChainTransactionReceivedEvent {
    private String userId;
    private long amount;
    private String txHash;
}