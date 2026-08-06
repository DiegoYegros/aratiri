package com.aratiri.infrastructure.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebSocketConfigTest {

  @Test
  void registerHandlers_usesCorsOriginsOutsideDev() {
    var handler = mock(com.aratiri.auth.infrastructure.notification.NotificationSocketHandler.class);
    Environment env = mock(Environment.class);
    when(env.getActiveProfiles()).thenReturn(new String[]{"prod"});

    WebSocketConfig config = new WebSocketConfig(handler, List.of("https://app.example.com"), env);
    WebSocketHandlerRegistry registry = mock(WebSocketHandlerRegistry.class);
    WebSocketHandlerRegistration registration = mock(WebSocketHandlerRegistration.class);
    when(registry.addHandler(eq(handler), anyString())).thenReturn(registration);

    config.registerWebSocketHandlers(registry);

    verify(registration).setAllowedOrigins("https://app.example.com");
    verify(registration, never()).setAllowedOriginPatterns(any(String[].class));
    verify(registration, never()).setAllowedOrigins("*");
  }

  @Test
  void registerHandlers_usesLocalhostPatternsInDev() {
    var handler = mock(com.aratiri.auth.infrastructure.notification.NotificationSocketHandler.class);
    Environment env = mock(Environment.class);
    when(env.getActiveProfiles()).thenReturn(new String[]{"dev"});

    WebSocketConfig config = new WebSocketConfig(handler, List.of("https://app.example.com"), env);
    WebSocketHandlerRegistry registry = mock(WebSocketHandlerRegistry.class);
    WebSocketHandlerRegistration registration = mock(WebSocketHandlerRegistration.class);
    when(registry.addHandler(eq(handler), anyString())).thenReturn(registration);

    config.registerWebSocketHandlers(registry);

    verify(registration).setAllowedOriginPatterns(
        "http://localhost:*",
        "http://127.0.0.1:*",
        "localhost:**");
    verify(registration, never()).setAllowedOrigins(any(String[].class));
  }
}
