package com.aratiri.generaldata.application;

import com.aratiri.config.AratiriProperties;
import com.aratiri.generaldata.application.port.in.GeneralDataPort;
import com.aratiri.generaldata.application.port.out.CurrencyConversionPort;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
public class GeneralDataAdapter implements GeneralDataPort {

    private final CurrencyConversionPort currencyConversionPort;
    private final AratiriProperties aratiriProperties;

    public GeneralDataAdapter(CurrencyConversionPort currencyConversionPort, AratiriProperties aratiriProperties) {
        this.currencyConversionPort = currencyConversionPort;
        this.aratiriProperties = aratiriProperties;
    }

    @Override
    @Cacheable(value = "btcPrice", key = "'allCurrencies'")
    public Map<String, BigDecimal> getCurrentBtcPrice() {
        List<String> fiatCurrencies = aratiriProperties.getFiatCurrencies();
        return currencyConversionPort.getCurrentBtcPrice(fiatCurrencies);
    }

    @Override
    public List<String> getFiatCurrencies() {
        return aratiriProperties.getFiatCurrencies();
    }
}
