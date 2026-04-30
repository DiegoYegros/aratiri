package com.aratiri.transactions.application.event;

public record OnChainTransactionReceivedEvent(
  String userId,
  long amount,
  String txHash,
  long outputIndex
) {

  public String getUserId() {
    return userId;
  }

  public long getAmount() {
    return amount;
  }

  public String getTxHash() {
    return txHash;
  }

  public long getOutputIndex() {
    return outputIndex;
  }
}
