package com.aratiri.accounts.infrastructure.currency;

import com.aratiri.generaldata.application.port.in.GeneralDataPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrencyConversionServiceAdapterTest {

    @Mock
    private GeneralDataPort generalDataPort;

    @Test
    void getCurrentBtcPrice_delegatesToPort() {
        CurrencyConversionServiceAdapter adapter = new CurrencyConversionServiceAdapter(generalDataPort);
        Map<String, BigDecimal> expected = Map.of("USD", BigDecimal.valueOf(60000));
        when(generalDataPort.getCurrentBtcPrice()).thenReturn(expected);

        Map<String, BigDecimal> result = adapter.getCurrentBtcPrice();

        assertEquals(expected, result);
    }
}
