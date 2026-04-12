package com.aratiri.generaldata.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record BtcPricePoint(Instant timestamp, BigDecimal price) {
}
