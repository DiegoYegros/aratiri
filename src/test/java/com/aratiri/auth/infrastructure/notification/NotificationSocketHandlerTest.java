package com.aratiri.auth.infrastructure.notification;

import com.aratiri.auth.domain.AuthProvider;
import com.aratiri.auth.domain.AuthUser;
import com.aratiri.auth.domain.Role;
import com.aratiri.auth.infrastructure.security.AratiriJwtPrincipalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

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
    void afterConnectionEstablished_shouldRejectMissingToken() throws Exception {
        URI uri = new URI("ws://localhost:8080/ws/notifications");

        when(session.getUri()).thenReturn(uri);

        handler.afterConnectionEstablished(session);

        verify(session).close(any(CloseStatus.class));
    }

    @Test
    void afterConnectionEstablished_shouldRejectInvalidToken() throws Exception {
        URI uri = new URI("ws://localhost:8080/ws/notifications?token=" + TOKEN);

        when(session.getUri()).thenReturn(uri);
        when(jwtDecoder.decode(TOKEN)).thenThrow(new JwtException("invalid"));

        handler.afterConnectionEstablished(session);

        verify(session).close(any(CloseStatus.class));
    }
}
