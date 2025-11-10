package com.aratiri.auth.application.port.out;

public interface NotificationPort {
    void sendNotification(String userId, String eventName, Object data);
}
