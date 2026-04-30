package com.aratiri.transactions.application.event;

public record InternalInvoiceCancelEvent(String paymentHash) {

  public String getPaymentHash() {
    return paymentHash;
  }
}
