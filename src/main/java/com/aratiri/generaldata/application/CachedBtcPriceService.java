package com.aratiri.generaldata.application;

import com.aratiri.generaldata.application.port.out.CurrencyConversionPort;
import com.aratiri.generaldata.domain.BtcPricePoint;
import com.aratiri.generaldata.domain.BtcPriceRange;
import com.aratiri.generaldata.domain.BtcPriceSnapshot;
import com.aratiri.infrastructure.configuration.AratiriProperties;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class CachedBtcPriceService {

    private final CurrencyConversionPort currencyConversionPort;
    private final AratiriProperties aratiriProperties;
    private final Clock clock;

    public CachedBtcPriceService(
            CurrencyConversionPort currencyConversionPort,
            AratiriProperties aratiriProperties,
            Clock clock
    ) {
        this.currencyConversionPort = currencyConversionPort;
        this.aratiriProperties = aratiriProperties;
        this.clock = clock;
    }

    @Cacheable(value = "btcPriceCurrent", key = "'allCurrencies'")
    public BtcPriceSnapshot getCurrentBtcPriceSnapshot() {
        Map<String, BigDecimal> prices = currencyConversionPort.getCurrentBtcPrice(aratiriProperties.getFiatCurrencies());
        return new BtcPriceSnapshot(Map.copyOf(prices), Instant.now(clock));
    }

    @Cacheable(value = "btcPriceHistory", key = "#currency + ':' + #range.code()")
    public List<BtcPricePoint> getBtcPriceHistory(String currency, BtcPriceRange range) {
        return List.copyOf(currencyConversionPort.getBtcPriceHistory(currency, range));
    }
}
