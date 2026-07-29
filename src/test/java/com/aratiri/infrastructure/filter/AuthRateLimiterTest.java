package com.aratiri.infrastructure.filter;

import com.aratiri.infrastructure.configuration.security.AratiriSecurityProperties.AuthRateLimit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AuthRateLimiterTest {

    private MutableClock clock;
    private AuthRateLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2025-01-01T00:00:00Z"));
        AuthRateLimit config = new AuthRateLimit();
        config.setEnabled(true);
        config.setRequestsPerWindow(3);
        config.setWindow(Duration.ofSeconds(60));
        config.setMaximumKeys(100);
        limiter = new AuthRateLimiter(config, clock);
    }

    @Test
    void allowsExactBoundaryThenDenies() {
        assertTrue(limiter.tryAcquire("ip|POST|/v1/auth/login").permitted());
        assertTrue(limiter.tryAcquire("ip|POST|/v1/auth/login").permitted());
        assertTrue(limiter.tryAcquire("ip|POST|/v1/auth/login").permitted());

        AuthRateLimiter.Decision denied = limiter.tryAcquire("ip|POST|/v1/auth/login");
        assertFalse(denied.permitted());
        assertEquals(60L, denied.retryAfterSeconds());
    }

    @Test
    void retryAfterShrinksAsWindowElapses() {
        for (int i = 0; i < 3; i++) {
            assertTrue(limiter.tryAcquire("ip|POST|/v1/auth/login").permitted());
        }
        clock.advance(Duration.ofSeconds(15));

        AuthRateLimiter.Decision denied = limiter.tryAcquire("ip|POST|/v1/auth/login");
        assertFalse(denied.permitted());
        assertEquals(45L, denied.retryAfterSeconds());
    }

    @Test
    void windowResetAllowsNewRequests() {
        for (int i = 0; i < 3; i++) {
            assertTrue(limiter.tryAcquire("ip|POST|/v1/auth/login").permitted());
        }
        assertFalse(limiter.tryAcquire("ip|POST|/v1/auth/login").permitted());

        clock.advance(Duration.ofSeconds(60));
        assertTrue(limiter.tryAcquire("ip|POST|/v1/auth/login").permitted());
    }

    @Test
    void separatesEndpoints() {
        for (int i = 0; i < 3; i++) {
            assertTrue(limiter.tryAcquire("ip|POST|/v1/auth/login").permitted());
        }
        assertFalse(limiter.tryAcquire("ip|POST|/v1/auth/login").permitted());
        assertTrue(limiter.tryAcquire("ip|POST|/v1/auth/register").permitted());
    }

    @Test
    void separatesRemoteAddresses() {
        for (int i = 0; i < 3; i++) {
            assertTrue(limiter.tryAcquire("10.0.0.1|POST|/v1/auth/login").permitted());
        }
        assertFalse(limiter.tryAcquire("10.0.0.1|POST|/v1/auth/login").permitted());
        assertTrue(limiter.tryAcquire("10.0.0.2|POST|/v1/auth/login").permitted());
    }

    @Test
    void maximumKeysBoundsDistinctEntries() {
        AuthRateLimit config = new AuthRateLimit();
        config.setRequestsPerWindow(100);
        config.setWindow(Duration.ofMinutes(1));
        config.setMaximumKeys(8);
        AuthRateLimiter bounded = new AuthRateLimiter(config, clock);

        for (int i = 0; i < 40; i++) {
            assertTrue(bounded.tryAcquire("key-" + i).permitted());
        }
        bounded.estimatedSize();
        assertTrue(bounded.estimatedSize() <= 8);
    }

    @Test
    void concurrentAcquiresDoNotOverrunLimit() throws Exception {
        AuthRateLimit config = new AuthRateLimit();
        config.setRequestsPerWindow(50);
        config.setWindow(Duration.ofMinutes(1));
        config.setMaximumKeys(100);
        AuthRateLimiter concurrentLimiter = new AuthRateLimiter(config, clock);

        int threads = 20;
        int attemptsPerThread = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicInteger allowed = new AtomicInteger();
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            tasks.add(() -> {
                for (int i = 0; i < attemptsPerThread; i++) {
                    if (concurrentLimiter.tryAcquire("same|POST|/v1/auth/login").permitted()) {
                        allowed.incrementAndGet();
                    }
                }
                return null;
            });
        }
        List<Future<Void>> futures = pool.invokeAll(tasks);
        for (Future<Void> future : futures) {
            future.get();
        }
        pool.shutdownNow();

        assertEquals(50, allowed.get());
    }

    @Test
    void rejectsNonsensicalConfig() {
        AuthRateLimit config = new AuthRateLimit();
        config.setRequestsPerWindow(0);
        assertThrows(IllegalStateException.class, () -> new AuthRateLimiter(config, clock));

        AuthRateLimit zeroWindow = new AuthRateLimit();
        zeroWindow.setWindow(Duration.ZERO);
        assertThrows(IllegalStateException.class, () -> new AuthRateLimiter(zeroWindow, clock));

        AuthRateLimit badKeys = new AuthRateLimit();
        badKeys.setMaximumKeys(0);
        assertThrows(IllegalStateException.class, () -> new AuthRateLimiter(badKeys, clock));
    }

    @Test
    void rejectsPositiveSubMillisecondWindowBeforeServingTraffic() {
        AuthRateLimit config = new AuthRateLimit();
        config.setWindow(Duration.ofNanos(500_000)); // 0.5ms → toMillis() == 0
        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> new AuthRateLimiter(config, clock));
        assertTrue(thrown.getMessage().contains("window"));
    }

    @Test
    void rejectsOverflowingAndAboveMaxWindowBeforeServingTraffic() {
        AuthRateLimit aboveMax = new AuthRateLimit();
        aboveMax.setWindow(Duration.ofDays(1).plusMillis(1));
        assertThrows(IllegalStateException.class, () -> new AuthRateLimiter(aboveMax, clock));

        AuthRateLimit overflowing = new AuthRateLimit();
        overflowing.setWindow(Duration.ofSeconds(Long.MAX_VALUE));
        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> new AuthRateLimiter(overflowing, clock));
        assertTrue(
                thrown.getMessage().contains("overflow")
                        || thrown.getMessage().contains("must be <="),
                () -> "unexpected message: " + thrown.getMessage());
    }

    @Test
    void storesValidatedWindowMillisOnce() {
        AuthRateLimit config = new AuthRateLimit();
        config.setWindow(Duration.ofSeconds(30));
        AuthRateLimiter built = new AuthRateLimiter(config, clock);
        assertEquals(30_000L, built.windowMillis());
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return Clock.fixed(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
