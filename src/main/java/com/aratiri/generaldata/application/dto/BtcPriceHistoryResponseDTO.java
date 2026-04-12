package com.aratiri.generaldata.application.dto;

import com.aratiri.generaldata.domain.BtcPricePoint;

import java.util.List;

public record BtcPriceHistoryResponseDTO(String currency, String range, List<BtcPricePoint> points) {
}
