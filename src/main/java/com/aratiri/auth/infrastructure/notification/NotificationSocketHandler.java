package com.aratiri.auth.infrastructure.notification;

import com.aratiri.auth.application.port.out.WsTicketStorePort;
import com.aratiri.auth.domain.WsTicket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.SubProtocolCapable;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class NotificationSocketHandler extends TextWebSocketHandler implements SubProtocolCapable {

  public static final String NOTIFICATIONS_SUBPROTOCOL = "aratiri.notifications.v1";
  private static final String QUERY_AUTH_NOT_ALLOWED = "query auth not allowed";
  private static final String INVALID_TICKET = "invalid ticket";

  private static final Logger logger = LoggerFactory.getLogger(NotificationSocketHandler.class);
  private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
  private final WsTicketStorePort ticketStore;

  public NotificationSocketHandler(WsTicketStorePort ticketStore) {
    this.ticketStore = ticketStore;
  }

  @Override
  public List<String> getSubProtocols() {
    return List.of(NOTIFICATIONS_SUBPROTOCOL);
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) throws Exception {
    if (hasQueryAuth(session.getUri())) {
      logger.warn("Closing WebSocket session: query auth not allowed.");
      session.close(CloseStatus.POLICY_VIOLATION.withReason(QUERY_AUTH_NOT_ALLOWED));
      return;
    }

    Optional<String> ticketId = extractTicketFromProtocols(session);
    if (ticketId.isEmpty()) {
      logger.warn("Closing WebSocket session: missing or malformed Sec-WebSocket-Protocol ticket.");
      session.close(CloseStatus.POLICY_VIOLATION.withReason(INVALID_TICKET));
      return;
    }

    Optional<WsTicket> ticket = ticketStore.consume(ticketId.get());
    if (ticket.isEmpty()) {
      logger.warn("Closing WebSocket session: ticket missing, expired, or already used.");
      session.close(CloseStatus.POLICY_VIOLATION.withReason(INVALID_TICKET));
      return;
    }

    String userId = ticket.get().userId();
    session.getAttributes().put("userId", userId);
    sessions.put(userId, session);
    logger.info("WebSocket connection established for user: {}", userId);
    session.sendMessage(new TextMessage("{\"event\": \"connected\", \"data\": \"Connection successful\"}"));
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    String userId = (String) session.getAttributes().get("userId");
    if (userId != null) {
      sessions.remove(userId);
      logger.info("WebSocket connection closed for user: {}. Status: {}", userId, status);
    }
  }

  public void sendMessage(String userId, String message) {
    WebSocketSession session = sessions.get(userId);
    if (session != null && session.isOpen()) {
      try {
        session.sendMessage(new TextMessage(message));
        logger.info("Sent WebSocket message to user: {}", userId);
      } catch (IOException e) {
        logger.error("Error sending WebSocket message to user: {}", userId, e);
      }
    } else {
      logger.warn("No active WebSocket session found for user: {}", userId);
    }
  }

  boolean hasActiveSession(String userId) {
    WebSocketSession session = sessions.get(userId);
    return session != null && session.isOpen();
  }

  static boolean hasQueryAuth(URI uri) {
    if (uri == null || uri.getQuery() == null || uri.getQuery().isBlank()) {
      return false;
    }
    for (String param : uri.getQuery().split("&")) {
      String key = param.split("=", 2)[0];
      if ("token".equals(key) || "ticket".equals(key)) {
        return true;
      }
    }
    return false;
  }

  static Optional<String> extractTicketFromProtocols(WebSocketSession session) {
    List<String> tokens = new ArrayList<>();
    List<String> headerValues = session.getHandshakeHeaders().get("Sec-WebSocket-Protocol");
    if (headerValues != null) {
      for (String value : headerValues) {
        if (value == null || value.isBlank()) {
          continue;
        }
        for (String part : value.split(",")) {
          String trimmed = part.trim();
          if (!trimmed.isEmpty()) {
            tokens.add(trimmed);
          }
        }
      }
    }

    boolean hasFixed = false;
    String ticket = null;
    for (String token : tokens) {
      if (NOTIFICATIONS_SUBPROTOCOL.equals(token)) {
        hasFixed = true;
      } else if (ticket == null) {
        ticket = token;
      } else {
        // More than one opaque token — malformed.
        return Optional.empty();
      }
    }
    if (!hasFixed || ticket == null || ticket.isBlank()) {
      return Optional.empty();
    }
    return Optional.of(ticket);
  }
}
