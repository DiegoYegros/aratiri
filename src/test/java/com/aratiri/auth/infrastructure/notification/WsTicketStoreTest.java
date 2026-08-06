package com.aratiri.auth.infrastructure.notification;

import com.aratiri.auth.domain.WsTicket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WsTicketStoreTest {

  private Clock clock;
  private WsTicketStore store;

  @BeforeEach
  void setUp() {
    clock = Clock.fixed(Instant.parse("2026-08-06T22:00:00Z"), ZoneOffset.UTC);
    store = new WsTicketStore(60, 30, clock);
  }

  @Test
  void issue_createsOpaqueTicketBoundToUser() {
    WsTicket ticket = store.issue("user-a");

    assertEquals("user-a", ticket.userId());
    assertEquals(Instant.parse("2026-08-06T22:01:00Z"), ticket.expiresAt());
    assertTrue(ticket.id().length() >= 43); // 32 bytes base64url without padding
    assertNotEquals("user-a", ticket.id());
  }

  @Test
  void consume_isSingleUse() {
    WsTicket issued = store.issue("user-a");

    Optional<WsTicket> first = store.consume(issued.id());
    Optional<WsTicket> second = store.consume(issued.id());

    assertTrue(first.isPresent());
    assertEquals("user-a", first.get().userId());
    assertTrue(second.isEmpty());
  }

  @Test
  void consume_rejectsExpiredTicket() {
    MutableClock mutable = new MutableClock(Instant.parse("2026-08-06T22:00:00Z"));
    store = new WsTicketStore(1, 30, mutable);
    WsTicket issued = store.issue("user-a");
    mutable.advance(Duration.ofSeconds(2));

    assertTrue(store.consume(issued.id()).isEmpty());
  }

  @Test
  void consume_concurrentDualRedeem_exactlyOneSuccess() throws Exception {
    WsTicket issued = store.issue("user-a");
    int threads = 16;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    CountDownLatch start = new CountDownLatch(1);
    AtomicInteger successes = new AtomicInteger();

    List<Future<?>> futures = new ArrayList<>();
    for (int i = 0; i < threads; i++) {
      futures.add(pool.submit(() -> {
        start.await();
        if (store.consume(issued.id()).isPresent()) {
          successes.incrementAndGet();
        }
        return null;
      }));
    }
    start.countDown();
    for (Future<?> future : futures) {
      future.get();
    }
    pool.shutdownNow();

    assertEquals(1, successes.get());
  }

  @Test
  void mintPermit_rateLimitsPerUser() {
    for (int i = 0; i < 30; i++) {
      assertTrue(store.tryAcquireMintPermit("user-a"));
    }
    assertFalse(store.tryAcquireMintPermit("user-a"));
    assertTrue(store.tryAcquireMintPermit("user-b"));
  }

  @Test
  void ttl_isCappedAt120Seconds() {
    store = new WsTicketStore(999, 30, clock);
    assertEquals(Duration.ofSeconds(120), store.ttl());
  }

  private static final class MutableClock extends Clock {
    private Instant instant;

    MutableClock(Instant instant) {
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
