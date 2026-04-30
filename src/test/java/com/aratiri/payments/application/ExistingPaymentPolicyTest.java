package com.aratiri.payments.application;

import com.aratiri.payments.domain.LightningPaymentStatus;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ExistingPaymentPolicyTest {

  private final ExistingPaymentPolicy policy = new ExistingPaymentPolicy();

  @Test
  void activeOrSettledNodePayment_rejectsSucceededAndInFlightPayments() {
    ExistingPaymentRejection succeeded =
        policy.activeOrSettledNodePayment(Optional.of(LightningPaymentStatus.SUCCEEDED)).orElseThrow();
    assertEquals(409, succeeded.status());
    assertEquals("Invoice payment is already in progress or has been settled.", succeeded.message());

    ExistingPaymentRejection inFlight =
        policy.activeOrSettledNodePayment(Optional.of(LightningPaymentStatus.IN_FLIGHT)).orElseThrow();
    assertEquals(409, inFlight.status());
  }

  @Test
  void activeOrSettledNodePayment_allowsFailedInitiatedUnknownAndMissingPayments() {
    assertTrue(policy.activeOrSettledNodePayment(Optional.of(LightningPaymentStatus.FAILED)).isEmpty());
    assertTrue(policy.activeOrSettledNodePayment(Optional.of(LightningPaymentStatus.INITIATED)).isEmpty());
    assertTrue(policy.activeOrSettledNodePayment(Optional.of(LightningPaymentStatus.UNKNOWN)).isEmpty());
    assertTrue(policy.activeOrSettledNodePayment(Optional.empty()).isEmpty());
  }

  @Test
  void settledAratiriInvoice_rejectsKnownSettledInvoices() {
    ExistingPaymentRejection rejection = policy.settledAratiriInvoice(true).orElseThrow();
    assertEquals(400, rejection.status());
    assertEquals("Invoice has already been paid", rejection.message());

    assertTrue(policy.settledAratiriInvoice(false).isEmpty());
  }

  @Test
  void settledExternalNodePayment_rejectsOnlySucceededPayments() {
    ExistingPaymentRejection rejection =
        policy.settledExternalNodePayment(Optional.of(LightningPaymentStatus.SUCCEEDED)).orElseThrow();
    assertEquals(400, rejection.status());
    assertEquals("Invoice has already been paid", rejection.message());

    assertTrue(policy.settledExternalNodePayment(Optional.of(LightningPaymentStatus.IN_FLIGHT)).isEmpty());
    assertTrue(policy.settledExternalNodePayment(Optional.empty()).isEmpty());
  }
}
