package com.aratiri.auth.application;

import com.aratiri.auth.application.port.out.NotificationPort;
import com.aratiri.auth.infrastructure.notification.NotificationSocketHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class NotificationsAdapter implements NotificationPort {

    private static final Logger logger = LoggerFactory.getLogger(NotificationsAdapter.class);

    private final NotificationSocketHandler socketHandler;
    private final ObjectMapper objectMapper;

    public NotificationsAdapter(NotificationSocketHandler socketHandler, ObjectMapper objectMapper) {
        this.socketHandler = socketHandler;
        this.objectMapper = objectMapper;
    }

    @Override
    public void sendNotification(String userId, String eventName, Object data) {
        try {
            String message = objectMapper.writeValueAsString(Map.of("event", eventName, "data", data));
            socketHandler.sendMessage(userId, message);
        } catch (Exception e) {
            logger.error("Error sending notification to user: {}", userId, e);
        }
    }
}