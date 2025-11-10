package com.aratiri.accounts.application.port.out;

import java.math.BigDecimal;
import java.util.Map;

public interface CurrencyConversionPort {

    Map<String, BigDecimal> getCurrentBtcPrice();
}
