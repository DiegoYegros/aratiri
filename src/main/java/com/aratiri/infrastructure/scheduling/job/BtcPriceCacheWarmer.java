package com.aratiri.infrastructure.scheduling.job;

import com.aratiri.generaldata.application.CachedBtcPriceService;
import com.aratiri.generaldata.domain.BtcPriceRange;
import com.aratiri.infrastructure.configuration.AratiriProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps {@code btcPriceCurrent} and {@code btcPriceHistory} warm so request threads rarely pay the
 * external currency-API latency. Uses the same cached service methods as the request path, so a
 * warm failure only leaves the cache empty and the request path fills it on demand. Never throws
 * out of the scheduler.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BtcPriceCacheWarmer {

    private final CachedBtcPriceService cachedBtcPriceService;
    private final AratiriProperties aratiriProperties;

    @Scheduled(fixedDelayString = "${aratiri.price-cache.warm-fixed-delay-ms:10000}")
    public void warmBtcPriceCache() {
        try {
            cachedBtcPriceService.getCurrentBtcPriceSnapshot();
        } catch (Exception e) {
            log.error("Failed to warm btcPriceCurrent cache; request path will fall back to on-demand fill", e);
        }
        for (String currency : aratiriProperties.getFiatCurrencies()) {
            for (BtcPriceRange range : BtcPriceRange.values()) {
                try {
                    cachedBtcPriceService.getBtcPriceHistory(currency, range);
                } catch (Exception e) {
                    log.error("Failed to warm btcPriceHistory cache for currency {} range {}",
                            currency, range.code(), e);
                }
            }
        }
    }
}
