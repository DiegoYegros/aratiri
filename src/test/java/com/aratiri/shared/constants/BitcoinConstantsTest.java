package com.aratiri.shared.constants;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class BitcoinConstantsTest {

    @Test
    void satoshisToBtc_long_convertsCorrectly() {
        assertEquals(0, BigDecimal.valueOf(1, 8).compareTo(BitcoinConstants.satoshisToBtc(1)));
        assertEquals(0, BigDecimal.ONE.compareTo(BitcoinConstants.satoshisToBtc(100_000_000)));
        assertEquals(0, BigDecimal.ZERO.compareTo(BitcoinConstants.satoshisToBtc(0)));
    }

    @Test
    void satoshisToBtc_bigDecimal_convertsCorrectly() {
        assertEquals(0, BigDecimal.valueOf(1, 8).compareTo(BitcoinConstants.satoshisToBtc(BigDecimal.ONE)));
        assertEquals(0, BigDecimal.ZERO.compareTo(BitcoinConstants.satoshisToBtc(BigDecimal.ZERO)));
    }

    @Test
    void btcToSatoshis_bigDecimal_convertsCorrectly() {
        assertEquals(0, BigDecimal.ONE.compareTo(BitcoinConstants.btcToSatoshis(BigDecimal.valueOf(1, 8))));
        assertEquals(0, BigDecimal.valueOf(100_000_000).compareTo(BitcoinConstants.btcToSatoshis(BigDecimal.ONE)));
    }

    @Test
    void btcToSatoshis_long_convertsCorrectly() {
        assertEquals(0, BigDecimal.valueOf(100_000_000).compareTo(BitcoinConstants.btcToSatoshis(1L)));
    }

    @Test
    void constants_areDefined() {
        assertEquals(100_000_000L, BitcoinConstants.SATOSHIS_PER_BTC_LONG);
        assertNotNull(BitcoinConstants.SATOSHIS_PER_BTC);
    }
}
