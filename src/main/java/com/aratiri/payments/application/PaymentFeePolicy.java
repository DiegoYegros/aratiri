package com.aratiri.payments.application;

import com.aratiri.shared.exception.AratiriException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
class PaymentFeePolicy {

  private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

  private final long lightningFixedFeeSat;
  private final BigDecimal lightningFeePercent;
  private final long onChainFixedFeeSat;
  private final BigDecimal onChainFeePercent;

  PaymentFeePolicy(
      @Value("${aratiri.payment.lightning.fee.fixed.sat:0}") long lightningFixedFeeSat,
      @Value("${aratiri.payment.lightning.fee.percent:0}") BigDecimal lightningFeePercent,
      @Value("${aratiri.payment.onchain.fee.fixed.sat:0}") long onChainFixedFeeSat,
      @Value("${aratiri.payment.onchain.fee.percent:0}") BigDecimal onChainFeePercent
  ) {
    this.lightningFixedFeeSat = lightningFixedFeeSat;
    this.lightningFeePercent = lightningFeePercent;
    this.onChainFixedFeeSat = onChainFixedFeeSat;
    this.onChainFeePercent = onChainFeePercent;
  }

  long lightningPlatformFee(long amountSat) {
    return calculateFee(amountSat, lightningFixedFeeSat, lightningFeePercent);
  }

  long onChainPlatformFee(long amountSat) {
    return calculateFee(amountSat, onChainFixedFeeSat, onChainFeePercent);
  }

  private long calculateFee(long amountSat, long fixedFeeSat, BigDecimal percentageFee) {
    BigDecimal effectivePercentage = percentageFee != null ? percentageFee : BigDecimal.ZERO;
    long percentageFeeSat = 0L;
    try {
      if (effectivePercentage.compareTo(BigDecimal.ZERO) > 0) {
        BigDecimal fee = effectivePercentage
            .multiply(BigDecimal.valueOf(amountSat))
            .divide(ONE_HUNDRED, 0, RoundingMode.CEILING);
        percentageFeeSat = fee.longValueExact();
      }
      return Math.addExact(fixedFeeSat, percentageFeeSat);
    } catch (ArithmeticException _) {
      throw new AratiriException(
          "Configured payment fees exceed supported limits.",
          HttpStatus.INTERNAL_SERVER_ERROR.value()
      );
    }
  }
}
