package com.aratiri.transactions.application;

public record OnChainCreditSettlement(
        String userId,
        long amountSat,
        String txHash,
        long outputIndex
) {
    public String referenceId() {
        return txHash + ":" + outputIndex;
    }
}
