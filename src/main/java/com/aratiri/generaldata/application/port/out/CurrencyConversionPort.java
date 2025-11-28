package com.aratiri.generaldata.application.port.out;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface CurrencyConversionPort {

    Map<String, BigDecimal> getCurrentBtcPrice(List<String> currencies);
}
