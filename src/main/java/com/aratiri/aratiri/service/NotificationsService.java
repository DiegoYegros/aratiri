package com.aratiri.aratiri.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface NotificationsService {
    SseEmitter subscribe(String userId);

    void sendNotification(String userId, String eventName, Object data);
}
