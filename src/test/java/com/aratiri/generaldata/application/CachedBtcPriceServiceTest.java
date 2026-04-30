package com.aratiri.generaldata.application;

import com.aratiri.generaldata.application.port.out.CurrencyConversionPort;
import com.aratiri.generaldata.domain.BtcPricePoint;
import com.aratiri.generaldata.domain.BtcPriceRange;
import com.aratiri.generaldata.domain.BtcPriceSnapshot;
import com.aratiri.infrastructure.configuration.AratiriProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CachedBtcPriceServiceTest {

    @Mock
    private CurrencyConversionPort currencyConversionPort;

    @Mock
    private AratiriProperties aratiriProperties;

    Clock clock = Clock.fixed(Instant.EPOCH, java.time.ZoneOffset.UTC);
    private CachedBtcPriceService service;

    @BeforeEach
    void setUp() {
        service = new CachedBtcPriceService(currencyConversionPort, aratiriProperties, clock);
    }

    @Test
    void getCurrentBtcPriceSnapshot_returnsSnapshot() {
        List<String> currencies = List.of("USD", "EUR");
        Map<String, BigDecimal> prices = Map.of("USD", BigDecimal.valueOf(60000));
        when(aratiriProperties.getFiatCurrencies()).thenReturn(currencies);
        when(currencyConversionPort.getCurrentBtcPrice(currencies)).thenReturn(prices);

        BtcPriceSnapshot result = service.getCurrentBtcPriceSnapshot();

        assertEquals(BigDecimal.valueOf(60000), result.prices().get("USD"));
        assertEquals(Instant.EPOCH, result.updatedAt());
    }

    @Test
    void getBtcPriceHistory_returnsHistory() {
        List<BtcPricePoint> expected = List.of(
                new BtcPricePoint(Instant.EPOCH, BigDecimal.valueOf(60000)));
        when(currencyConversionPort.getBtcPriceHistory("USD", BtcPriceRange.ONE_HOUR)).thenReturn(expected);

        List<BtcPricePoint> result = service.getBtcPriceHistory("USD", BtcPriceRange.ONE_HOUR);

        assertEquals(1, result.size());
        assertEquals(BigDecimal.valueOf(60000), result.getFirst().price());
    }
}
