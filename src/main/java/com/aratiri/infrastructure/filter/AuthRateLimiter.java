package com.aratiri.infrastructure.filter;

import com.aratiri.infrastructure.configuration.security.AratiriSecurityProperties.AuthRateLimit;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Bounded, in-process fixed-window rate limiter keyed by caller identity and route.
 * <p>
 * Concurrency: updates go through {@code ConcurrentHashMap#compute}, so two threads
 * cannot both observe a free slot and both increment past the limit for the same key.
 * Eviction under {@code maximumKeys} pressure can free slots — this is intentional and
 * documented as an operational bound, not a hard global quota.
 * <p>
 * Window length is validated once at construction ({@link AuthRateLimit#validatedWindowMillis()})
 * and stored as milliseconds so per-request paths never call {@link Duration#toMillis()}.
 */
public class AuthRateLimiter {

    private final Cache<String, WindowState> buckets;
    private final int requestsPerWindow;
    private final long windowMillis;
    private final Clock clock;

    public AuthRateLimiter(AuthRateLimit config, Clock clock) {
        Objects.requireNonNull(config, "config");
        config.validate();
        this.requestsPerWindow = config.getRequestsPerWindow();
        this.windowMillis = config.validatedWindowMillis();
        this.clock = Objects.requireNonNull(clock, "clock");
        long expireAfterAccessMillis = Math.multiplyExact(this.windowMillis, 2L);
        this.buckets = Caffeine.newBuilder()
                .maximumSize(config.getMaximumKeys())
                .expireAfterAccess(Duration.ofMillis(expireAfterAccessMillis))
                .build();
    }

    public Decision tryAcquire(String key) {
        Objects.requireNonNull(key, "key");
        long nowMs = clock.millis();
        AtomicReference<Decision> decision = new AtomicReference<>();

        buckets.asMap().compute(key, (ignored, existing) -> {
            if (existing == null || nowMs - existing.windowStartEpochMs() >= windowMillis) {
                decision.set(Decision.permit());
                return new WindowState(nowMs, 1);
            }
            if (existing.count() >= requestsPerWindow) {
                long remainingMs = windowMillis - (nowMs - existing.windowStartEpochMs());
                long retryAfterSeconds = Math.max(1L, (remainingMs + 999L) / 1000L);
                decision.set(Decision.deny(retryAfterSeconds));
                return existing;
            }
            decision.set(Decision.permit());
            return new WindowState(existing.windowStartEpochMs(), existing.count() + 1);
        });

        return decision.get();
    }

    long windowMillis() {
        return windowMillis;
    }

    int estimatedSize() {
        buckets.cleanUp();
        return (int) buckets.estimatedSize();
    }

    public record Decision(boolean permitted, long retryAfterSeconds) {
        static Decision permit() {
            return new Decision(true, 0L);
        }

        static Decision deny(long retryAfterSeconds) {
            return new Decision(false, retryAfterSeconds);
        }
    }

    private record WindowState(long windowStartEpochMs, int count) {
    }
}
