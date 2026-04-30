package com.aratiri.payments.domain;

public record LightningPayment(
    LightningPaymentStatus status,
    String failureReason,
    long feeSat,
    long feeMsat
) {

  public long feeSatRoundedUp() {
    if (feeSat > 0) {
      return feeSat;
    }
    if (feeMsat <= 0) {
      return 0L;
    }
    return (feeMsat + 999) / 1000;
  }
}
