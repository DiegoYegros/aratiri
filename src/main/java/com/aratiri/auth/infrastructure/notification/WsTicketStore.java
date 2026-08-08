package com.aratiri.auth.infrastructure.notification;

import com.aratiri.auth.application.port.out.WsTicketStorePort;
import com.aratiri.auth.domain.WsTicket;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * In-process opaque WS ticket store. Tickets expire by TTL and are consumed
 * atomically on first successful redeem (compare-and-delete).
 */
@Component
public class WsTicketStore implements WsTicketStorePort {

  static final int MAX_TTL_SECONDS = 120;
  static final int DEFAULT_TTL_SECONDS = 60;
  static final int TICKET_BYTES = 32;
  static final int DEFAULT_MINTS_PER_MINUTE = 30;

  private final Cache<String, WsTicket> tickets;
  private final Cache<String, MintWindow> mintWindows;
  private final SecureRandom secureRandom = new SecureRandom();
  private final Clock clock;
  private final Duration ttl;
  private final int mintsPerMinute;

  public WsTicketStore(
      @Value("${aratiri.notifications.ws-ticket-ttl-seconds:60}") int ttlSeconds,
      @Value("${aratiri.notifications.ws-ticket-mints-per-minute:30}") int mintsPerMinute,
      Clock clock) {
    this.clock = Objects.requireNonNull(clock, "clock");
    int boundedTtl = Math.clamp(ttlSeconds, 1, MAX_TTL_SECONDS);
    this.ttl = Duration.ofSeconds(boundedTtl);
    this.mintsPerMinute = Math.max(1, mintsPerMinute);
    this.tickets = Caffeine.newBuilder()
        .expireAfterWrite(this.ttl)
        .maximumSize(100_000)
        .build();
    this.mintWindows = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofMinutes(2))
        .maximumSize(100_000)
        .build();
  }

  Duration ttl() {
    return ttl;
  }

  @Override
  public WsTicket issue(String userId) {
    Objects.requireNonNull(userId, "userId");
    Instant now = clock.instant();
    String id = generateTicketId();
    WsTicket ticket = new WsTicket(id, userId, now.plus(ttl), now);
    tickets.put(id, ticket);
    return ticket;
  }

  @Override
  public Optional<WsTicket> consume(String ticketId) {
    if (ticketId == null || ticketId.isBlank()) {
      return Optional.empty();
    }
    AtomicReference<WsTicket> consumed = new AtomicReference<>();
    tickets.asMap().compute(ticketId, (id, existing) -> {
      if (existing == null) {
        return null;
      }
      Instant now = clock.instant();
      if (!existing.expiresAt().isAfter(now)) {
        return null;
      }
      consumed.set(existing);
      return null;
    });
    return Optional.ofNullable(consumed.get());
  }

  @Override
  public boolean tryAcquireMintPermit(String userId) {
    Objects.requireNonNull(userId, "userId");
    long nowMs = clock.millis();
    AtomicReference<Boolean> permitted = new AtomicReference<>(false);
    mintWindows.asMap().compute(userId, (ignored, existing) -> {
      if (existing == null || nowMs - existing.windowStartEpochMs() >= 60_000L) {
        permitted.set(true);
        return new MintWindow(nowMs, new AtomicInteger(1));
      }
      if (existing.count().get() >= mintsPerMinute) {
        permitted.set(false);
        return existing;
      }
      existing.count().incrementAndGet();
      permitted.set(true);
      return existing;
    });
    return Boolean.TRUE.equals(permitted.get());
  }

  private String generateTicketId() {
    byte[] bytes = new byte[TICKET_BYTES];
    secureRandom.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private record MintWindow(long windowStartEpochMs, AtomicInteger count) {
  }
}
