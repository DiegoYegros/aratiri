package com.aratiri.payments.application.event;

import java.time.LocalDateTime;

public record PaymentSentEvent(
  String userId,
  String transactionId,
  Long amount,
  String paymentHash,
  LocalDateTime timestamp,
  String memo
) {

  public String getUserId() {
    return userId;
  }

  public String getTransactionId() {
    return transactionId;
  }

  public Long getAmount() {
    return amount;
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
