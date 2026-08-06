package com.aratiri.auth.application.port.out;

import com.aratiri.auth.domain.WsTicket;

import java.util.Optional;

public interface WsTicketStorePort {
  WsTicket issue(String userId);

  /**
   * Atomically consume a ticket. Returns empty if missing, expired, or already used.
   */
  Optional<WsTicket> consume(String ticketId);

  boolean tryAcquireMintPermit(String userId);
}
