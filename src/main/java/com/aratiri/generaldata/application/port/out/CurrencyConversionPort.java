package com.aratiri.generaldata.application.port.out;

import com.aratiri.generaldata.domain.BtcPricePoint;
import com.aratiri.generaldata.domain.BtcPriceRange;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface CurrencyConversionPort {

    Map<String, BigDecimal> getCurrentBtcPrice(List<String> currencies);

    List<BtcPricePoint> getBtcPriceHistory(String currency, BtcPriceRange range);
}
