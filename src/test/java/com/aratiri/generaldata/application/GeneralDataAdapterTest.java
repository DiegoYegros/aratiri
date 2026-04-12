package com.aratiri.generaldata.application;

import com.aratiri.generaldata.application.dto.BtcPriceHistoryResponseDTO;
import com.aratiri.generaldata.application.dto.CurrentBtcPriceResponseDTO;
import com.aratiri.generaldata.domain.BtcPricePoint;
import com.aratiri.generaldata.domain.BtcPriceRange;
import com.aratiri.generaldata.domain.BtcPriceSnapshot;
import com.aratiri.infrastructure.configuration.AratiriProperties;
import com.aratiri.shared.exception.AratiriException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeneralDataAdapterTest {

    @Mock
    private CachedBtcPriceService cachedBtcPriceService;

    @Mock
    private AratiriProperties aratiriProperties;

    private GeneralDataAdapter generalDataAdapter;

    @BeforeEach
    void setUp() {
        generalDataAdapter = new GeneralDataAdapter(cachedBtcPriceService, aratiriProperties);
        when(aratiriProperties.getFiatCurrencies()).thenReturn(List.of("usd", "eur"));
    }

    @Test
    void getCurrentBtcPrice_shouldReturnNormalizedCurrencyAndCachedTimestamp() {
        Instant updatedAt = Instant.parse("2026-04-12T12:00:00Z");
        when(cachedBtcPriceService.getCurrentBtcPriceSnapshot())
                .thenReturn(new BtcPriceSnapshot(Map.of("usd", BigDecimal.valueOf(85234.12)), updatedAt));

        CurrentBtcPriceResponseDTO result = generalDataAdapter.getCurrentBtcPrice("USD");

        assertEquals("usd", result.currency());
        assertEquals(BigDecimal.valueOf(85234.12), result.price());
        assertEquals(updatedAt, result.updatedAt());
    }

    @Test
    void getCurrentBtcPrice_shouldRejectUnsupportedCurrency() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> generalDataAdapter.getCurrentBtcPrice("gbp")
        );

        assertEquals("Unsupported currency [gbp]. Allowed values: usd, eur", exception.getMessage());
    }

    @Test
    void getCurrentBtcPrice_shouldReturnServiceUnavailableWhenPriceMissing() {
        when(cachedBtcPriceService.getCurrentBtcPriceSnapshot())
                .thenReturn(new BtcPriceSnapshot(Map.of("eur", BigDecimal.valueOf(78000)), Instant.parse("2026-04-12T12:00:00Z")));

        AratiriException exception = assertThrows(
                AratiriException.class,
                () -> generalDataAdapter.getCurrentBtcPrice("usd")
        );

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), exception.getStatus());
        assertEquals("Current BTC price is unavailable for currency [usd].", exception.getMessage());
    }

    @Test
    void getBtcPriceHistory_shouldReturnNormalizedInputs() {
        List<BtcPricePoint> points = List.of(
                new BtcPricePoint(Instant.parse("2026-04-12T11:30:00Z"), BigDecimal.valueOf(84000)),
                new BtcPricePoint(Instant.parse("2026-04-12T12:00:00Z"), BigDecimal.valueOf(84200))
        );
        when(cachedBtcPriceService.getBtcPriceHistory("eur", BtcPriceRange.SEVEN_DAYS)).thenReturn(points);

        BtcPriceHistoryResponseDTO result = generalDataAdapter.getBtcPriceHistory("EUR", "7D");

        assertEquals("eur", result.currency());
        assertEquals("7d", result.range());
        assertEquals(points, result.points());
        verify(cachedBtcPriceService).getBtcPriceHistory("eur", BtcPriceRange.SEVEN_DAYS);
    }

    @Test
    void getBtcPriceHistory_shouldReturnServiceUnavailableWhenProviderFails() {
        when(cachedBtcPriceService.getBtcPriceHistory("usd", BtcPriceRange.ONE_HOUR))
                .thenThrow(new IllegalStateException("Provider unavailable"));

        AratiriException exception = assertThrows(
                AratiriException.class,
                () -> generalDataAdapter.getBtcPriceHistory("usd", "1h")
        );

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), exception.getStatus());
        assertEquals("BTC price history is unavailable for currency [usd] and range [1h].", exception.getMessage());
    }
}
