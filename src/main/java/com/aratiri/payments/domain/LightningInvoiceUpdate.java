package com.aratiri.payments.domain;

public record LightningInvoiceUpdate(
    String paymentRequest,
    State state,
    long amountPaidSat,
    long addIndex,
    long settleIndex
) {

  public enum State {
    OPEN,
    SETTLED,
    CANCELED,
    ACCEPTED,
    UNKNOWN
  }
}
