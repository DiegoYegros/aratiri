package com.aratiri.auth.infrastructure.notification;

import com.aratiri.auth.domain.AuthProvider;
import com.aratiri.auth.domain.AuthUser;
import com.aratiri.auth.domain.Role;
import com.aratiri.auth.infrastructure.security.AratiriJwtPrincipalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationSocketHandlerTest {

    @Mock
    private JwtDecoder jwtDecoder;

    @Mock
    private AratiriJwtPrincipalService principalService;

    @Mock
    private WebSocketSession session;

    private NotificationSocketHandler handler;

    private static final String USER_ID = "user-123";
    private static final String TOKEN = "valid-jwt-token";

    @BeforeEach
    void setUp() {
        handler = new NotificationSocketHandler(jwtDecoder, principalService);
    }

    @Test
    void afterConnectionEstablished_shouldAcceptValidToken() throws Exception {
        URI uri = new URI("ws://localhost:8080/ws/notifications?token=" + TOKEN);
        Map<String, Object> attributes = new HashMap<>();

        Jwt jwt = Jwt.withTokenValue(TOKEN)
                .header("alg", "none")
                .claim("sub", "user")
                .build();
        AuthUser user = new AuthUser(USER_ID, "Test User", "test@example.com", AuthProvider.LOCAL, Role.USER);

        when(session.getUri()).thenReturn(uri);
        when(session.getAttributes()).thenReturn(attributes);
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwt);
        when(principalService.resolveUser(jwt)).thenReturn(user);

        handler.afterConnectionEstablished(session);

        verify(session).sendMessage(any(TextMessage.class));
        verify(session, never()).close(any(CloseStatus.class));
        assertEquals(USER_ID, attributes.get("userId"));
    }

    @Test
    void afterConnectionEstablished_shouldRejectInvalidToken() throws Exception {
        URI uri = new URI("ws://localhost:8080/ws/notifications?token=" + TOKEN);

        when(session.getUri()).thenReturn(uri);
        when(jwtDecoder.decode(TOKEN)).thenThrow(new JwtException("invalid"));

        handler.afterConnectionEstablished(session);

        verify(session).close(any(CloseStatus.class));
    }

    @ParameterizedTest
    @MethodSource("invalidHandshakeUris")
    void afterConnectionEstablished_shouldRejectMissingToken(URI uri) throws Exception {
        when(session.getUri()).thenReturn(uri);

        handler.afterConnectionEstablished(session);

        verify(session).close(any(CloseStatus.class));
    }

    static Stream<URI> invalidHandshakeUris() {
        return Stream.of(
                null,
                URI.create("ws://localhost:8080/ws/notifications"),
                URI.create("ws://localhost:8080/ws/notifications?foo=bar"),
                URI.create("ws://localhost:8080/ws/notifications?other=value&foo=bar")
        );
    }

    @Test
    void afterConnectionEstablished_shouldRejectGenericException() throws Exception {
        URI uri = new URI("ws://localhost:8080/ws/notifications?token=" + TOKEN);

        when(session.getUri()).thenReturn(uri);
        when(jwtDecoder.decode(TOKEN)).thenThrow(new RuntimeException("unexpected"));

        handler.afterConnectionEstablished(session);

        verify(session).close(any(CloseStatus.class));
    }

    @Test
    void afterConnectionClosed_shouldRemoveSession() throws Exception {
        URI uri = new URI("ws://localhost:8080/ws/notifications?token=" + TOKEN);
        Map<String, Object> attributes = new HashMap<>();
        Jwt jwt = Jwt.withTokenValue(TOKEN).header("alg", "none").claim("sub", "user").build();
        AuthUser user = new AuthUser(USER_ID, "Test User", "test@example.com", AuthProvider.LOCAL, Role.USER);

        when(session.getUri()).thenReturn(uri);
        when(session.getAttributes()).thenReturn(attributes);
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwt);
        when(principalService.resolveUser(jwt)).thenReturn(user);

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
        URI uri = new URI("ws://localhost:8080/ws/notifications?token=" + TOKEN);
        Map<String, Object> attributes = new HashMap<>();
        Jwt jwt = Jwt.withTokenValue(TOKEN).header("alg", "none").claim("sub", "user").build();
        AuthUser user = new AuthUser(USER_ID, "Test User", "test@example.com", AuthProvider.LOCAL, Role.USER);

        when(session.getUri()).thenReturn(uri);
        when(session.getAttributes()).thenReturn(attributes);
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwt);
        when(principalService.resolveUser(jwt)).thenReturn(user);
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
        URI uri = new URI("ws://localhost:8080/ws/notifications?token=" + TOKEN);
        Map<String, Object> attributes = new HashMap<>();
        Jwt jwt = Jwt.withTokenValue(TOKEN).header("alg", "none").claim("sub", "user").build();
        AuthUser user = new AuthUser(USER_ID, "Test User", "test@example.com", AuthProvider.LOCAL, Role.USER);

        when(session.getUri()).thenReturn(uri);
        when(session.getAttributes()).thenReturn(attributes);
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwt);
        when(principalService.resolveUser(jwt)).thenReturn(user);
        when(session.isOpen()).thenReturn(true);

        handler.afterConnectionEstablished(session);

        doThrow(new IOException("connection lost")).when(session).sendMessage(any(TextMessage.class));
        handler.sendMessage(USER_ID, "test message");

        verify(session, times(2)).sendMessage(any(TextMessage.class));
    }

    @Test
    void sendMessage_shouldLogWarningWhenSessionNotOpen() throws Exception {
        URI uri = new URI("ws://localhost:8080/ws/notifications?token=" + TOKEN);
        Map<String, Object> attributes = new HashMap<>();
        Jwt jwt = Jwt.withTokenValue(TOKEN).header("alg", "none").claim("sub", "user").build();
        AuthUser user = new AuthUser(USER_ID, "Test User", "test@example.com", AuthProvider.LOCAL, Role.USER);

        when(session.getUri()).thenReturn(uri);
        when(session.getAttributes()).thenReturn(attributes);
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwt);
        when(principalService.resolveUser(jwt)).thenReturn(user);
        when(session.isOpen()).thenReturn(false);

        handler.afterConnectionEstablished(session);

        handler.sendMessage(USER_ID, "test message");

        verify(session, times(1)).sendMessage(any(TextMessage.class));
    }
}
