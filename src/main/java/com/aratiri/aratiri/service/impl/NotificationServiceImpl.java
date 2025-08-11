package com.aratiri.aratiri.service.impl;

import com.aratiri.aratiri.service.NotificationsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class NotificationServiceImpl implements NotificationsService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationServiceImpl.class);
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String userId) {
        SseEmitter emitter = getEmitter(userId);
        this.emitters.put(userId, emitter);
        try {
            emitter.send(SseEmitter.event().name("connected").data("Connected to the Notifications Service"));
        } catch (IOException e) {
            logger.error("Error sending emit signal to user: {}", userId, e);
        }
        logger.info("New Notifications subscriber: {}", userId);
        return emitter;
    }

    private SseEmitter getEmitter(String userId) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        emitter.onCompletion(() -> {
            logger.info("Emitter completed for user: {}", userId);
            this.emitters.remove(userId);
        });
        emitter.onTimeout(() -> {
            logger.info("Emitter timeout for user: {}", userId);
            emitter.complete();
            this.emitters.remove(userId);
        });
        emitter.onError(e -> {
            logger.error("Emitter error for user: {}", userId, e);
            this.emitters.remove(userId);
        });
        return emitter;
    }

    @Async
    public void sendNotification(String userId, String eventName, Object data) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
                logger.info("Notification sent to user: {} - Event: {}", userId, eventName);
            } catch (IOException e) {
                logger.error("Error sending notification to user: {}. Removing emitter.", userId, e);
                emitters.remove(userId);
            }
        } else {
            logger.warn("Active emitter not found for user: {}", userId);
        }
    }
}
