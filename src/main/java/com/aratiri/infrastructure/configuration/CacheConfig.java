package com.aratiri.infrastructure.configuration;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

@Configuration
public class CacheConfig {

    @Bean
    public CacheManager cacheManager(AratiriProperties aratiriProperties) {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(List.of(
                buildCache("btcPriceCurrent", Duration.ofSeconds(aratiriProperties.getBtcPriceCurrentCacheTtlSeconds()), 1),
                buildCache("btcPriceHistory", Duration.ofSeconds(aratiriProperties.getBtcPriceHistoryCacheTtlSeconds()), 128)
        ));
        return cacheManager;
    }

    private CaffeineCache buildCache(String name, Duration ttl, long maximumSize) {
        return new CaffeineCache(
                name,
                Caffeine.newBuilder()
                        .expireAfterWrite(ttl)
                        .maximumSize(maximumSize)
                        .build()
        );
    }
}
