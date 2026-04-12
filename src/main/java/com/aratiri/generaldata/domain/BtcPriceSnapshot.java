package com.aratiri.generaldata.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record BtcPriceSnapshot(Map<String, BigDecimal> prices, Instant updatedAt) {
}
