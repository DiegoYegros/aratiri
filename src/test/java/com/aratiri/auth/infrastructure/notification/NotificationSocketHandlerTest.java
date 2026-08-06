package com.aratiri.auth.infrastructure.notification;

import com.aratiri.auth.application.port.out.WsTicketStorePort;
import com.aratiri.auth.domain.WsTicket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationSocketHandlerTest {

  @Mock
  private WsTicketStorePort ticketStore;

  @Mock
  private WebSocketSession session;

  private NotificationSocketHandler handler;

  private static final String USER_ID = "user-123";
  private static final String TICKET = "opaque-ticket-value";

  @BeforeEach
  void setUp() {
    handler = new NotificationSocketHandler(ticketStore);
  }

  @Test
  void afterConnectionEstablished_shouldAcceptValidTicketProtocol() throws Exception {
    Map<String, Object> attributes = new HashMap<>();
    HttpHeaders headers = protocolHeaders(NotificationSocketHandler.NOTIFICATIONS_SUBPROTOCOL, TICKET);
    WsTicket ticket = new WsTicket(TICKET, USER_ID, Instant.now().plusSeconds(60), Instant.now());

    when(session.getUri()).thenReturn(URI.create("ws://localhost:8080/v1/notifications/subscribe"));
    when(session.getHandshakeHeaders()).thenReturn(headers);
    when(session.getAttributes()).thenReturn(attributes);
    when(ticketStore.consume(TICKET)).thenReturn(Optional.of(ticket));

    handler.afterConnectionEstablished(session);

    verify(session).sendMessage(any(TextMessage.class));
    verify(session, never()).close(any(CloseStatus.class));
    assertEquals(USER_ID, attributes.get("userId"));
    assertEquals(List.of(NotificationSocketHandler.NOTIFICATIONS_SUBPROTOCOL), handler.getSubProtocols());
  }

  @Test
  void afterConnectionEstablished_shouldRejectQueryToken() throws Exception {
    when(session.getUri()).thenReturn(
        URI.create("ws://localhost:8080/v1/notifications/subscribe?token=access-jwt"));

    handler.afterConnectionEstablished(session);

    verify(session).close(argThat(status ->
        status.getCode() == CloseStatus.POLICY_VIOLATION.getCode()
            && "query auth not allowed".equals(status.getReason())));
    verify(ticketStore, never()).consume(any());
    verify(session, never()).sendMessage(any());
  }

  @Test
  void afterConnectionEstablished_shouldRejectQueryTicket() throws Exception {
    when(session.getUri()).thenReturn(
        URI.create("ws://localhost:8080/v1/notifications/subscribe?ticket=" + TICKET));

    handler.afterConnectionEstablished(session);

    verify(session).close(argThat(status ->
        status.getCode() == CloseStatus.POLICY_VIOLATION.getCode()
            && "query auth not allowed".equals(status.getReason())));
    verify(ticketStore, never()).consume(any());
  }

  @ParameterizedTest
  @MethodSource("invalidProtocolHeaders")
  void afterConnectionEstablished_shouldRejectMalformedProtocols(HttpHeaders headers) throws Exception {
    when(session.getUri()).thenReturn(URI.create("ws://localhost:8080/v1/notifications/subscribe"));
    when(session.getHandshakeHeaders()).thenReturn(headers);

    handler.afterConnectionEstablished(session);

    verify(session).close(any(CloseStatus.class));
    verify(ticketStore, never()).consume(any());
  }

  static Stream<HttpHeaders> invalidProtocolHeaders() {
    return Stream.of(
        new HttpHeaders(),
        protocolHeaders(NotificationSocketHandler.NOTIFICATIONS_SUBPROTOCOL),
        protocolHeaders(TICKET),
        protocolHeaders(NotificationSocketHandler.NOTIFICATIONS_SUBPROTOCOL, TICKET, "extra"),
        protocolHeaders("wrong.protocol", TICKET)
    );
  }

  @Test
  void afterConnectionEstablished_shouldRejectMissingOrUsedTicket() throws Exception {
    HttpHeaders headers = protocolHeaders(NotificationSocketHandler.NOTIFICATIONS_SUBPROTOCOL, TICKET);
    when(session.getUri()).thenReturn(URI.create("ws://localhost:8080/v1/notifications/subscribe"));
    when(session.getHandshakeHeaders()).thenReturn(headers);
    when(ticketStore.consume(TICKET)).thenReturn(Optional.empty());

    handler.afterConnectionEstablished(session);

    verify(session).close(any(CloseStatus.class));
    verify(session, never()).sendMessage(any());
  }

  @Test
  void afterConnectionClosed_shouldRemoveSession() throws Exception {
    Map<String, Object> attributes = new HashMap<>();
    HttpHeaders headers = protocolHeaders(NotificationSocketHandler.NOTIFICATIONS_SUBPROTOCOL, TICKET);
    WsTicket ticket = new WsTicket(TICKET, USER_ID, Instant.now().plusSeconds(60), Instant.now());

    when(session.getUri()).thenReturn(URI.create("ws://localhost:8080/v1/notifications/subscribe"));
    when(session.getHandshakeHeaders()).thenReturn(headers);
    when(session.getAttributes()).thenReturn(attributes);
    when(ticketStore.consume(TICKET)).thenReturn(Optional.of(ticket));

    handler.afterConnectionEstablished(session);
    handler.afterConnectionClosed(session, CloseStatus.NORMAL);
    clearInvocations(session);

    handler.sendMessage(USER_ID, "test message");

    verify(session, never()).sendMessage(any(TextMessage.class));
  }

  @Test
  void afterConnectionClosed_shouldHandleNoUserId() throws IOException {
    Map<String, Object> attributes = new HashMap<>();
    when(session.getAttributes()).thenReturn(attributes);

    handler.afterConnectionClosed(session, CloseStatus.NORMAL);

    verify(session, never()).close();
  }

  @Test
  void sendMessage_shouldSendWhenSessionOpen() throws Exception {
    Map<String, Object> attributes = new HashMap<>();
    HttpHeaders headers = protocolHeaders(NotificationSocketHandler.NOTIFICATIONS_SUBPROTOCOL, TICKET);
    WsTicket ticket = new WsTicket(TICKET, USER_ID, Instant.now().plusSeconds(60), Instant.now());

    when(session.getUri()).thenReturn(URI.create("ws://localhost:8080/v1/notifications/subscribe"));
    when(session.getHandshakeHeaders()).thenReturn(headers);
    when(session.getAttributes()).thenReturn(attributes);
    when(ticketStore.consume(TICKET)).thenReturn(Optional.of(ticket));
    when(session.isOpen()).thenReturn(true);

    handler.afterConnectionEstablished(session);
    handler.sendMessage(USER_ID, "test message");

    verify(session, atLeastOnce()).sendMessage(any(TextMessage.class));
  }

  @Test
  void sendMessage_shouldLogWarningWhenNoSession() {
    handler.sendMessage("unknown-user", "test message");

    verifyNoInteractions(session);
  }

  @Test
  void sendMessage_shouldHandleIOException() throws Exception {
    Map<String, Object> attributes = new HashMap<>();
    HttpHeaders headers = protocolHeaders(NotificationSocketHandler.NOTIFICATIONS_SUBPROTOCOL, TICKET);
    WsTicket ticket = new WsTicket(TICKET, USER_ID, Instant.now().plusSeconds(60), Instant.now());

    when(session.getUri()).thenReturn(URI.create("ws://localhost:8080/v1/notifications/subscribe"));
    when(session.getHandshakeHeaders()).thenReturn(headers);
    when(session.getAttributes()).thenReturn(attributes);
    when(ticketStore.consume(TICKET)).thenReturn(Optional.of(ticket));
    when(session.isOpen()).thenReturn(true);

    handler.afterConnectionEstablished(session);

    doThrow(new IOException("connection lost")).when(session).sendMessage(any(TextMessage.class));
    handler.sendMessage(USER_ID, "test message");

    verify(session, times(2)).sendMessage(any(TextMessage.class));
  }

  @Test
  void sendMessage_shouldLogWarningWhenSessionNotOpen() throws Exception {
    Map<String, Object> attributes = new HashMap<>();
    HttpHeaders headers = protocolHeaders(NotificationSocketHandler.NOTIFICATIONS_SUBPROTOCOL, TICKET);
    WsTicket ticket = new WsTicket(TICKET, USER_ID, Instant.now().plusSeconds(60), Instant.now());

    when(session.getUri()).thenReturn(URI.create("ws://localhost:8080/v1/notifications/subscribe"));
    when(session.getHandshakeHeaders()).thenReturn(headers);
    when(session.getAttributes()).thenReturn(attributes);
    when(ticketStore.consume(TICKET)).thenReturn(Optional.of(ticket));
    when(session.isOpen()).thenReturn(false);

    handler.afterConnectionEstablished(session);
    handler.sendMessage(USER_ID, "test message");

    verify(session, times(1)).sendMessage(any(TextMessage.class));
  }

  @Test
  void hasQueryAuth_detectsTokenAndTicketKeys() {
    assertTrue(NotificationSocketHandler.hasQueryAuth(
        URI.create("ws://host/v1/notifications/subscribe?token=x")));
    assertTrue(NotificationSocketHandler.hasQueryAuth(
        URI.create("ws://host/v1/notifications/subscribe?ticket=y")));
    assertFalse(NotificationSocketHandler.hasQueryAuth(
        URI.create("ws://host/v1/notifications/subscribe?foo=bar")));
    assertFalse(NotificationSocketHandler.hasQueryAuth(null));
  }

  private static HttpHeaders protocolHeaders(String... protocols) {
    HttpHeaders headers = new HttpHeaders();
    if (protocols.length > 0) {
      headers.add("Sec-WebSocket-Protocol", String.join(", ", protocols));
    }
    return headers;
  }
}
