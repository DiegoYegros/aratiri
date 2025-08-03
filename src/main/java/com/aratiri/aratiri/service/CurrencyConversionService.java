package com.aratiri.aratiri.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface CurrencyConversionService {

    Map<String, BigDecimal> getCurrentBtcPrice();

    List<String> getFiatCurrencies();
}