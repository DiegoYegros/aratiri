package com.aratiri.aratiri.constant;

import java.math.BigDecimal;

public final class BitcoinConstants {
    public static final long SATOSHIS_PER_BTC_LONG = 100_000_000L;
    public static final int SATOSHIS_PER_BTC_INTEGER = 100_000_000;
    public static final BigDecimal SATOSHIS_PER_BTC = new BigDecimal(SATOSHIS_PER_BTC_LONG);

    private BitcoinConstants() {
    }

    public static BigDecimal satoshisToBtc(BigDecimal satoshis) {
        return satoshis.divide(SATOSHIS_PER_BTC, 8, java.math.RoundingMode.HALF_UP);
    }

    public static BigDecimal satoshisToBtc(long satoshis) {
        BigDecimal bigDecimalSats = new BigDecimal(satoshis);
        return bigDecimalSats.divide(SATOSHIS_PER_BTC, 8, java.math.RoundingMode.HALF_UP);
    }

    public static BigDecimal btcToSatoshis(BigDecimal btc) {
        return btc.multiply(SATOSHIS_PER_BTC);
    }

    public static BigDecimal btcToSatoshis(long btc) {
        BigDecimal bigDecimalBtc = new BigDecimal(btc);
        return bigDecimalBtc.multiply(SATOSHIS_PER_BTC);
    }
}