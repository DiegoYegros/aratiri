package com.aratiri.shared.constants;

import com.aratiri.shared.constants.BitcoinConstants;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BitcoinConstantsTest {

    @Test
    void satoshisToBtc_shouldConvertLongToCorrectBtc() {
        BigDecimal result = BitcoinConstants.satoshisToBtc(100000000L);
        assertEquals(new BigDecimal("1.00000000"), result);
    }

    @Test
    void satoshisToBtc_shouldConvertSmallAmount() {
        BigDecimal result = BitcoinConstants.satoshisToBtc(1L);
        assertEquals(new BigDecimal("0.00000001"), result);
    }

    @Test
    void satoshisToBtc_shouldConvertZero() {
        BigDecimal result = BitcoinConstants.satoshisToBtc(0L);
        assertEquals(new BigDecimal("0.00000000"), result);
    }

    @Test
    void satoshisToBtc_fromBigDecimal_shouldConvert() {
        BigDecimal sats = new BigDecimal("50000000");
        BigDecimal result = BitcoinConstants.satoshisToBtc(sats);
        assertEquals(new BigDecimal("0.50000000"), result);
    }

    @Test
    void btcToSatoshis_shouldConvertBigDecimalToSats() {
        BigDecimal btc = new BigDecimal("1.0");
        BigDecimal result = BitcoinConstants.btcToSatoshis(btc);
        assertEquals(new BigDecimal("100000000.0"), result);
    }

    @Test
    void btcToSatoshis_shouldConvertSmallBtcAmount() {
        BigDecimal btc = new BigDecimal("0.00000001");
        BigDecimal result = BitcoinConstants.btcToSatoshis(btc);
        assertEquals(new BigDecimal("1.00000000"), result);
    }

    @Test
    void btcToSatoshis_fromLong_shouldConvert() {
        BigDecimal result = BitcoinConstants.btcToSatoshis(2L);
        assertEquals(new BigDecimal("200000000"), result);
    }

    @Test
    void satoshisPerBtc_shouldBe100Million() {
        assertEquals(100_000_000L, BitcoinConstants.SATOSHIS_PER_BTC_LONG);
        assertEquals(new BigDecimal("100000000"), BitcoinConstants.SATOSHIS_PER_BTC);
    }

    @Test
    void roundTrip_shouldPreservValue() {
        long originalSats = 12345678L;
        BigDecimal btc = BitcoinConstants.satoshisToBtc(originalSats);
        BigDecimal backToSats = BitcoinConstants.btcToSatoshis(btc);
        assertEquals(originalSats, backToSats.longValue());
    }
}
