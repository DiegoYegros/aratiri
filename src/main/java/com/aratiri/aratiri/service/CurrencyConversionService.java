package com.aratiri.aratiri.service;

import java.math.BigDecimal;

public interface CurrencyConversionService {
    BigDecimal getCurrentBtcPrice(String currency);
}
