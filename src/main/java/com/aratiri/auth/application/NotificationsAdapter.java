package com.aratiri.auth.application;

import com.aratiri.auth.application.port.out.NotificationPort;
import com.aratiri.auth.infrastructure.notification.NotificationSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

@Service
public class NotificationsAdapter implements NotificationPort {

    private static final Logger logger = LoggerFactory.getLogger(NotificationsAdapter.class);

    private final NotificationSocketHandler socketHandler;
    private final JsonMapper jsonMapper;

    public NotificationsAdapter(NotificationSocketHandler socketHandler, JsonMapper jsonMapper) {
        this.socketHandler = socketHandler;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void sendNotification(String userId, String eventName, Object data) {
        try {
            String message = jsonMapper.writeValueAsString(Map.of("event", eventName, "data", data));
            socketHandler.sendMessage(userId, message);
        } catch (Exception e) {
            logger.error("Error sending notification to user: {}", userId, e);
        }
    }
}