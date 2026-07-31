package com.aratiri.bitcoin;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class BitcoinAmountsTest {

  @Test
  void satoshisToBtc_long_convertsCorrectly() {
    assertEquals(0, BigDecimal.valueOf(1, 8).compareTo(BitcoinAmounts.satoshisToBtc(1)));
    assertEquals(0, BigDecimal.ONE.compareTo(BitcoinAmounts.satoshisToBtc(100_000_000)));
    assertEquals(0, BigDecimal.ZERO.compareTo(BitcoinAmounts.satoshisToBtc(0)));
  }

  @Test
  void satoshisToBtc_bigDecimal_convertsCorrectly() {
    assertEquals(0, BigDecimal.valueOf(1, 8).compareTo(BitcoinAmounts.satoshisToBtc(BigDecimal.ONE)));
    assertEquals(0, BigDecimal.ZERO.compareTo(BitcoinAmounts.satoshisToBtc(BigDecimal.ZERO)));
  }

  @Test
  void btcToSatoshis_bigDecimal_convertsCorrectly() {
    assertEquals(0, BigDecimal.ONE.compareTo(BitcoinAmounts.btcToSatoshis(BigDecimal.valueOf(1, 8))));
    assertEquals(0, BigDecimal.valueOf(100_000_000).compareTo(BitcoinAmounts.btcToSatoshis(BigDecimal.ONE)));
  }

  @Test
  void btcToSatoshis_long_convertsCorrectly() {
    assertEquals(0, BigDecimal.valueOf(100_000_000).compareTo(BitcoinAmounts.btcToSatoshis(1L)));
  }

  @Test
  void constants_areDefined() {
    assertEquals(100_000_000L, BitcoinAmounts.SATOSHIS_PER_BTC_LONG);
    assertNotNull(BitcoinAmounts.SATOSHIS_PER_BTC);
  }
}
