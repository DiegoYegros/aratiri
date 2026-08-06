package com.aratiri.infrastructure.configuration;

import com.aratiri.auth.infrastructure.notification.NotificationSocketHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

  private final NotificationSocketHandler notificationSocketHandler;
  private final List<String> allowedOrigins;
  private final Environment env;

  public WebSocketConfig(
      NotificationSocketHandler notificationSocketHandler,
      @Value("${aratiri.cors.allowed.origins}") List<String> allowedOrigins,
      Environment env) {
    this.notificationSocketHandler = notificationSocketHandler;
    this.allowedOrigins = allowedOrigins;
    this.env = env;
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    WebSocketHandlerRegistration registration =
        registry.addHandler(notificationSocketHandler, "/v1/notifications/subscribe");
    applyCorsAllowlist(registration);
  }

  private void applyCorsAllowlist(WebSocketHandlerRegistration registration) {
    List<String> activeProfiles = Arrays.asList(env.getActiveProfiles());
    if (activeProfiles.contains("dev") || activeProfiles.contains("local")) {
      registration.setAllowedOriginPatterns(
          "http://localhost:*",
          "http://127.0.0.1:*",
          "localhost:**");
    } else {
      registration.setAllowedOrigins(allowedOrigins.toArray(String[]::new));
    }
  }
}
