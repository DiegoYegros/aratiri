package com.aratiri.aratiri.handler;

import com.aratiri.aratiri.entity.UserEntity;
import com.aratiri.aratiri.service.UserService;
import com.aratiri.aratiri.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class NotificationSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(NotificationSocketHandler.class);
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final JwtUtil jwtUtil;
    private final UserService userService;

    public NotificationSocketHandler(JwtUtil jwtUtil, UserService userService) {
        this.jwtUtil = jwtUtil;
        this.userService = userService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String userId = extractUserIdFromSession(session);
        if (userId == null || userId.isEmpty()) {
            logger.warn("Closing session due to missing or invalid token during handshake.");
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Missing or invalid JWT"));
            return;
        }

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

    private String extractUserIdFromSession(WebSocketSession session) {
        try {
            String token = getTokenFromQuery(session.getUri());
            if (token == null) {
                logger.warn("Token not found in WebSocket URI query parameters.");
                return null;
            }

            String email = jwtUtil.extractUsername(token);
            if (email == null) {
                logger.warn("Could not extract email from token.");
                return null;
            }

            String userId = userService.findByEmail(email)
                    .map(UserEntity::getId)
                    .orElse(null);

            if (userId != null) {
                session.getAttributes().put("userId", userId);
            } else {
                logger.warn("No user found for email: {}", email);
            }

            return userId;
        } catch (Exception e) {
            logger.error("Failed to extract user from session URI: {}", e.getMessage());
            return null;
        }
    }

    private String getTokenFromQuery(URI uri) {
        if (uri == null || uri.getQuery() == null) {
            return null;
        }
        String[] params = uri.getQuery().split("&");
        for (String param : params) {
            String[] keyValue = param.split("=", 2);
            if (keyValue.length == 2 && "token".equals(keyValue[0])) {
                return keyValue[1];
            }
        }
        return null;
    }
}