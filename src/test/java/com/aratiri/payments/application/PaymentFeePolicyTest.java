package com.aratiri.payments.application;

import com.aratiri.shared.exception.AratiriException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class PaymentFeePolicyTest {

  @Test
  void lightningPlatformFee_addsFixedFeeAndCeilingPercentageFee() {
    PaymentFeePolicy policy = new PaymentFeePolicy(2L, new BigDecimal("1.5"), 0L, null);

    assertEquals(4L, policy.lightningPlatformFee(101L));
  }

  @Test
  void onChainPlatformFee_treatsNullPercentageAsZero() {
    PaymentFeePolicy policy = new PaymentFeePolicy(0L, null, 7L, null);

    assertEquals(7L, policy.onChainPlatformFee(10_000L));
  }

  @Test
  void platformFee_rejectsArithmeticOverflow() {
    PaymentFeePolicy policy = new PaymentFeePolicy(Long.MAX_VALUE, BigDecimal.ONE, 0L, null);

    AratiriException exception = assertThrows(AratiriException.class, () -> policy.lightningPlatformFee(1L));
    assertEquals(500, exception.getStatus());
    assertTrue(exception.getMessage().contains("Configured payment fees exceed supported limits"));
  }
}
