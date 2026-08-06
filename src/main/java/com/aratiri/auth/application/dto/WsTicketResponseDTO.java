package com.aratiri.auth.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class WsTicketResponseDTO {
  private String ticket;
  private long expiresInSeconds;
  private String expiresAt;
}
