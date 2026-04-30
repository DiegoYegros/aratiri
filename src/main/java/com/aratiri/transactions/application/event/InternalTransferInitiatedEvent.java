package com.aratiri.transactions.application.event;

public record InternalTransferInitiatedEvent(
  String transactionId,
  String senderId,
  String receiverId,
  long amountSat,
  String paymentHash
) {

  public String getTransactionId() {
    return transactionId;
  }

  public String getSenderId() {
    return senderId;
  }

  public String getReceiverId() {
    return receiverId;
  }

  public long getAmountSat() {
    return amountSat;
  }

  public String getPaymentHash() {
    return paymentHash;
  }
}
