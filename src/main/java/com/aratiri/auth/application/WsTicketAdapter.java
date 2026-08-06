package com.aratiri.auth.application;

import com.aratiri.auth.application.dto.WsTicketResponseDTO;
import com.aratiri.auth.application.port.in.WsTicketPort;
import com.aratiri.auth.application.port.out.WsTicketStorePort;
import com.aratiri.auth.domain.WsTicket;
import com.aratiri.errors.ApplicationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Service
public class WsTicketAdapter implements WsTicketPort {

  private static final DateTimeFormatter EXPIRES_AT =
      DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC);

  private final WsTicketStorePort ticketStore;

  public WsTicketAdapter(WsTicketStorePort ticketStore) {
    this.ticketStore = ticketStore;
  }

  @Override
  public WsTicketResponseDTO mintTicket(String userId) {
    if (!ticketStore.tryAcquireMintPermit(userId)) {
      throw new ApplicationException(
          "Too many WebSocket ticket requests. Please try again later.",
          HttpStatus.TOO_MANY_REQUESTS.value());
    }
    WsTicket ticket = ticketStore.issue(userId);
    long expiresInSeconds = Math.max(1L, Duration.between(ticket.issuedAt(), ticket.expiresAt()).getSeconds());
    return WsTicketResponseDTO.builder()
        .ticket(ticket.id())
        .expiresInSeconds(expiresInSeconds)
        .expiresAt(EXPIRES_AT.format(ticket.expiresAt()))
        .build();
  }
}
