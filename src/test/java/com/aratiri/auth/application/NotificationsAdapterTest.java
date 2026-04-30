package com.aratiri.auth.application;

import com.aratiri.auth.infrastructure.notification.NotificationSocketHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationsAdapterTest {

    @Mock
    private NotificationSocketHandler socketHandler;

    @Mock
    private JsonMapper jsonMapper;

    private NotificationsAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new NotificationsAdapter(socketHandler, jsonMapper);
    }

    @Test
    void sendNotification_sendsMessage() throws Exception {
        when(jsonMapper.writeValueAsString(any())).thenReturn("{\"event\":\"test\"}");

        adapter.sendNotification("user-1", "payment.completed", "data");

        verify(socketHandler).sendMessage("user-1", "{\"event\":\"test\"}");
    }

    @Test
    void sendNotification_swallowsException() throws Exception {
        when(jsonMapper.writeValueAsString(any())).thenThrow(new RuntimeException("serialization error"));

        adapter.sendNotification("user-1", "payment.completed", "data");

        verify(socketHandler, never()).sendMessage(anyString(), anyString());
    }
}
