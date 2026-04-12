package com.aratiri.generaldata.domain;

import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

public enum BtcPriceRange {
    ONE_HOUR("1h", Duration.ofHours(1), 1),
    TWENTY_FOUR_HOURS("24h", Duration.ofHours(24), 1),
    SEVEN_DAYS("7d", Duration.ofDays(7), 7);

    private final String code;
    private final Duration duration;
    private final int coingeckoDays;

    BtcPriceRange(String code, Duration duration, int coingeckoDays) {
        this.code = code;
        this.duration = duration;
        this.coingeckoDays = coingeckoDays;
    }

    public String code() {
        return code;
    }

    public Duration duration() {
        return duration;
    }

    public int coingeckoDays() {
        return coingeckoDays;
    }

    public static BtcPriceRange fromCode(String code) {
        String normalizedCode = code == null ? "" : code.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(value -> value.code.equals(normalizedCode))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported range [" + code + "]. Allowed values: " + supportedCodes()
                ));
    }

    private static String supportedCodes() {
        return Arrays.stream(values())
                .map(BtcPriceRange::code)
                .collect(Collectors.joining(", "));
    }
}
