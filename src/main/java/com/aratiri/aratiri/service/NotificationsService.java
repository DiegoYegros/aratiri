package com.aratiri.aratiri.service;

public interface NotificationsService {
    void sendNotification(String userId, String eventName, Object data);
}
