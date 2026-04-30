package com.aratiri.transactions.application.event;

import java.time.LocalDateTime;

public record InternalTransferCompletedEvent(
  String senderId,
  String receiverId,
  Long amountSat,
  String paymentHash,
  LocalDateTime timestamp,
  String memo
) {

  public String getSenderId() {
    return senderId;
  }

  public String getReceiverId() {
    return receiverId;
  }

  public Long getAmountSat() {
    return amountSat;
  }

  public String getPaymentHash() {
    return paymentHash;
  }

  public LocalDateTime getTimestamp() {
    return timestamp;
  }

  public String getMemo() {
    return memo;
  }
}
