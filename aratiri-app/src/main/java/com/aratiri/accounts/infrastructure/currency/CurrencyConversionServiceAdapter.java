package com.aratiri.accounts.infrastructure.currency;

import com.aratiri.accounts.application.port.out.CurrencyConversionPort;
import com.aratiri.service.CurrencyConversionService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class CurrencyConversionServiceAdapter implements CurrencyConversionPort {

    private final CurrencyConversionService currencyConversionService;

    public CurrencyConversionServiceAdapter(CurrencyConversionService currencyConversionService) {
        this.currencyConversionService = currencyConversionService;
    }

    @Override
    public Map<String, BigDecimal> getCurrentBtcPrice() {
        return currencyConversionService.getCurrentBtcPrice();
    }
}
