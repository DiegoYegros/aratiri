package com.aratiri.payments.domain;

public record LightningInvoiceUpdate(
    String paymentRequest,
    String paymentHash,
    State state,
    long amountPaidSat,
    long addIndex,
    long settleIndex
) {

  public LightningInvoiceUpdate(
      String paymentRequest,
      State state,
      long amountPaidSat,
      long addIndex,
      long settleIndex
  ) {
    this(paymentRequest, null, state, amountPaidSat, addIndex, settleIndex);
  }

  public enum State {
    OPEN,
    SETTLED,
    CANCELED,
    ACCEPTED,
    UNKNOWN
  }
}
