package com.aratiri.payments.application;

import com.aratiri.payments.domain.LightningPaymentStatus;
import com.aratiri.shared.exception.AratiriException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
class ExistingPaymentPolicy {

  Optional<ExistingPaymentRejection> activeOrSettledNodePayment(Optional<LightningPaymentStatus> paymentState) {
    if (paymentState.isPresent()
        && (paymentState.get() == LightningPaymentStatus.SUCCEEDED
        || paymentState.get() == LightningPaymentStatus.IN_FLIGHT)) {
      return Optional.of(new ExistingPaymentRejection(
          "Invoice payment is already in progress or has been settled.",
          HttpStatus.CONFLICT.value()
      ));
    }
    return Optional.empty();
  }

  Optional<ExistingPaymentRejection> settledAratiriInvoice(boolean settledInvoiceExists) {
    if (settledInvoiceExists) {
      return Optional.of(new ExistingPaymentRejection("Invoice has already been paid", HttpStatus.BAD_REQUEST.value()));
    }
    return Optional.empty();
  }

  Optional<ExistingPaymentRejection> settledExternalNodePayment(Optional<LightningPaymentStatus> paymentState) {
    if (paymentState.isPresent() && paymentState.get() == LightningPaymentStatus.SUCCEEDED) {
      return Optional.of(new ExistingPaymentRejection("Invoice has already been paid", HttpStatus.BAD_REQUEST.value()));
    }
    return Optional.empty();
  }
}

record ExistingPaymentRejection(String message, int status) {

  AratiriException toException() {
    return new AratiriException(message, status);
  }
}
