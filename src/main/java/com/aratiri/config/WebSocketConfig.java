package com.aratiri.config;

import com.aratiri.handler.NotificationSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final NotificationSocketHandler notificationSocketHandler;

    public WebSocketConfig(NotificationSocketHandler notificationSocketHandler) {
        this.notificationSocketHandler = notificationSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(notificationSocketHandler, "/v1/notifications/subscribe")
                .setAllowedOrigins("*");
    }
}