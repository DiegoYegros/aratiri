package com.aratiri.bitcoin;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Satoshi/BTC amount conversion helpers. Monetary values in ledger and payment
 * flows remain integer satoshis; these helpers are for fiat/display boundaries.
 */
public final class BitcoinAmounts {
  public static final long SATOSHIS_PER_BTC_LONG = 100_000_000L;
  public static final BigDecimal SATOSHIS_PER_BTC = new BigDecimal(SATOSHIS_PER_BTC_LONG);

  private BitcoinAmounts() {
  }

  public static BigDecimal satoshisToBtc(BigDecimal satoshis) {
    return satoshis.divide(SATOSHIS_PER_BTC, 8, RoundingMode.HALF_UP);
  }

  public static BigDecimal satoshisToBtc(long satoshis) {
    return satoshisToBtc(new BigDecimal(satoshis));
  }

  public static BigDecimal btcToSatoshis(BigDecimal btc) {
    return btc.multiply(SATOSHIS_PER_BTC);
  }

  public static BigDecimal btcToSatoshis(long btc) {
    return btcToSatoshis(new BigDecimal(btc));
  }
}
