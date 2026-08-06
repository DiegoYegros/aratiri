package com.aratiri.auth.application;

import com.aratiri.auth.application.dto.WsTicketResponseDTO;
import com.aratiri.auth.application.port.out.WsTicketStorePort;
import com.aratiri.auth.domain.WsTicket;
import com.aratiri.errors.ApplicationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WsTicketAdapterTest {

  @Mock
  private WsTicketStorePort ticketStore;

  @InjectMocks
  private WsTicketAdapter adapter;

  @Test
  void mintTicket_returnsOpaqueTicketMetadata() {
    Instant issuedAt = Instant.parse("2026-08-06T22:00:00Z");
    Instant expiresAt = Instant.parse("2026-08-06T22:01:00Z");
    when(ticketStore.tryAcquireMintPermit("user-1")).thenReturn(true);
    when(ticketStore.issue("user-1")).thenReturn(new WsTicket("ticket-abc", "user-1", expiresAt, issuedAt));

    WsTicketResponseDTO response = adapter.mintTicket("user-1");

    assertEquals("ticket-abc", response.getTicket());
    assertEquals(60L, response.getExpiresInSeconds());
    assertEquals("2026-08-06T22:01:00Z", response.getExpiresAt());
    verify(ticketStore).issue("user-1");
  }

  @Test
  void mintTicket_rateLimited_throws429() {
    when(ticketStore.tryAcquireMintPermit("user-1")).thenReturn(false);

    ApplicationException ex = assertThrows(ApplicationException.class, () -> adapter.mintTicket("user-1"));

    assertEquals(429, ex.getStatus());
  }
}
