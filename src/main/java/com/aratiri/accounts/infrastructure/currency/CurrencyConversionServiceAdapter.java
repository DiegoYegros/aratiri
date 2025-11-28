package com.aratiri.accounts.infrastructure.currency;

import com.aratiri.accounts.application.port.out.CurrencyConversionPort;
import com.aratiri.generaldata.application.port.in.GeneralDataPort;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class CurrencyConversionServiceAdapter implements CurrencyConversionPort {

    private final GeneralDataPort generalDataPort;

    public CurrencyConversionServiceAdapter(GeneralDataPort generalDataPort) {
        this.generalDataPort = generalDataPort;
    }

    @Override
    public Map<String, BigDecimal> getCurrentBtcPrice() {
        return generalDataPort.getCurrentBtcPrice();
    }
}
