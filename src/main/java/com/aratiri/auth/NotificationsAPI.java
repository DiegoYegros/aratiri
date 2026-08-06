package com.aratiri.auth;

import com.aratiri.auth.application.dto.WsTicketResponseDTO;
import com.aratiri.auth.application.port.in.WsTicketPort;
import com.aratiri.infrastructure.web.ErrorResponse;
import com.aratiri.infrastructure.web.context.AratiriContext;
import com.aratiri.infrastructure.web.context.AratiriCtx;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/notifications")
@Tag(name = "Notifications", description = "Notification WebSocket ticket minting")
public class NotificationsAPI {

  private final WsTicketPort wsTicketPort;

  public NotificationsAPI(WsTicketPort wsTicketPort) {
    this.wsTicketPort = wsTicketPort;
  }

  @PostMapping("/ws-ticket")
  @Operation(
      summary = "Mint a short-lived notification WebSocket ticket",
      description = "Requires a Bearer access JWT. Returns a single-use opaque ticket for "
          + "Sec-WebSocket-Protocol handshake on GET /v1/notifications/subscribe. "
          + "Tickets expire quickly and must not appear in WebSocket URLs."
  )
  @ApiResponses(value = {
      @ApiResponse(
          responseCode = "200",
          description = "Ticket minted",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = WsTicketResponseDTO.class)
          )
      ),
      @ApiResponse(
          responseCode = "401",
          description = "Authentication required",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = ErrorResponse.class)
          )
      ),
      @ApiResponse(
          responseCode = "429",
          description = "Mint rate limit exceeded",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = ErrorResponse.class)
          )
      )
  })
  public ResponseEntity<WsTicketResponseDTO> mintWsTicket(@AratiriCtx AratiriContext ctx) {
    if (ctx == null || ctx.user() == null || ctx.user().getId() == null) {
      return ResponseEntity.status(401).build();
    }
    return ResponseEntity.ok()
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(wsTicketPort.mintTicket(ctx.user().getId()));
  }
}
