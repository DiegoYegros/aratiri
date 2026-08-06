package com.aratiri.auth.application.port.in;

import com.aratiri.auth.application.dto.WsTicketResponseDTO;

public interface WsTicketPort {
  WsTicketResponseDTO mintTicket(String userId);
}
