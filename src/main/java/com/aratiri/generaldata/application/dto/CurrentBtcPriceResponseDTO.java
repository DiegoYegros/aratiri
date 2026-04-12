package com.aratiri.generaldata.application.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record CurrentBtcPriceResponseDTO(String currency, BigDecimal price, Instant updatedAt) {
}
