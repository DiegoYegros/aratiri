package com.aratiri.infrastructure.scheduling.job;

import com.aratiri.generaldata.application.CachedBtcPriceService;
import com.aratiri.generaldata.domain.BtcPriceRange;
import com.aratiri.infrastructure.configuration.AratiriProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BtcPriceCacheWarmerTest {

    @Mock
    private CachedBtcPriceService cachedBtcPriceService;

    @Mock
    private AratiriProperties aratiriProperties;

    private BtcPriceCacheWarmer warmer;

    @BeforeEach
    void setUp() {
        warmer = new BtcPriceCacheWarmer(cachedBtcPriceService, aratiriProperties);
    }

    @Test
    void warmBtcPriceCache_populatesCurrentAndHistoryForEveryConfiguredCurrencyAndRange() {
        when(aratiriProperties.getFiatCurrencies()).thenReturn(List.of("usd", "eur"));

        warmer.warmBtcPriceCache();

        verify(cachedBtcPriceService).getCurrentBtcPriceSnapshot();
        for (String currency : List.of("usd", "eur")) {
            for (BtcPriceRange range : BtcPriceRange.values()) {
                verify(cachedBtcPriceService).getBtcPriceHistory(currency, range);
            }
        }
    }

    @Test
    void warmBtcPriceCache_continuesOnFailureAndNeverThrows() {
        when(aratiriProperties.getFiatCurrencies()).thenReturn(List.of("usd", "eur"));
        doThrow(new IllegalStateException("coingecko down")).when(cachedBtcPriceService).getCurrentBtcPriceSnapshot();
        doThrow(new IllegalStateException("no history")).when(cachedBtcPriceService)
                .getBtcPriceHistory("eur", BtcPriceRange.SEVEN_DAYS);

        assertDoesNotThrow(() -> warmer.warmBtcPriceCache());

        verify(cachedBtcPriceService).getCurrentBtcPriceSnapshot();
        verify(cachedBtcPriceService, times(3)).getBtcPriceHistory(org.mockito.ArgumentMatchers.eq("usd"), org.mockito.ArgumentMatchers.any());
        verify(cachedBtcPriceService, times(3)).getBtcPriceHistory(org.mockito.ArgumentMatchers.eq("eur"), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void warmBtcPriceCache_noConfiguredCurrencies_onlyWarmsCurrent() {
        when(aratiriProperties.getFiatCurrencies()).thenReturn(List.of());

        warmer.warmBtcPriceCache();

        verify(cachedBtcPriceService).getCurrentBtcPriceSnapshot();
        verify(cachedBtcPriceService, times(0)).getBtcPriceHistory(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any());
    }
}
