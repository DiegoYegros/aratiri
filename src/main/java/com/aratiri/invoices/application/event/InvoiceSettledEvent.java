package com.aratiri.invoices.application.event;

import java.time.LocalDateTime;

public record InvoiceSettledEvent(
  String userId,
  Long amount,
  String paymentHash,
  LocalDateTime timestamp,
  String memo
) {

  public String getUserId() {
    return userId;
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
