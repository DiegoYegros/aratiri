package com.aratiri.admin;

import com.aratiri.webhooks.application.WebhookAdminService;
import com.aratiri.webhooks.application.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminWebhooksAPITest {

    @Mock
    private WebhookAdminService webhookAdminService;

    private AdminWebhooksAPI adminWebhooksAPI;

    @BeforeEach
    void setUp() {
        adminWebhooksAPI = new AdminWebhooksAPI(webhookAdminService);
    }

    @Test
    void createWebhook() {
        CreateWebhookEndpointRequestDTO request = new CreateWebhookEndpointRequestDTO();
        request.setName("Test");
        request.setUrl("https://example.com");
        request.setEventTypes(Set.of("payment.succeeded"));
        request.setEnabled(true);

        WebhookSecretResponseDTO response = WebhookSecretResponseDTO.builder()
                .id(UUID.randomUUID())
                .signingSecret("secret123")
                .build();
        when(webhookAdminService.createEndpoint(any())).thenReturn(response);

        ResponseEntity<WebhookSecretResponseDTO> result = adminWebhooksAPI.createWebhook(request);

        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        assertEquals("secret123", result.getBody().getSigningSecret());
        verify(webhookAdminService).createEndpoint(request);
    }

    @Test
    void listWebhooks() {
        when(webhookAdminService.listEndpoints()).thenReturn(List.of());

        ResponseEntity<List<WebhookEndpointResponseDTO>> response = adminWebhooksAPI.listWebhooks();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void getWebhook() {
        UUID id = UUID.randomUUID();
        WebhookEndpointResponseDTO dto = WebhookEndpointResponseDTO.builder()
                .id(id)
                .name("Test")
                .url("https://example.com")
                .eventTypes(Set.of("payment.succeeded"))
                .enabled(true)
                .build();
        when(webhookAdminService.getEndpoint(id)).thenReturn(dto);

        ResponseEntity<WebhookEndpointResponseDTO> response = adminWebhooksAPI.getWebhook(id);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Test", response.getBody().getName());
        assertEquals(id, response.getBody().getId());
    }

    @Test
    void updateWebhook() {
        UUID id = UUID.randomUUID();
        UpdateWebhookEndpointRequestDTO request = new UpdateWebhookEndpointRequestDTO();
        request.setName("Updated");
        request.setUrl("https://example.com/new");
        request.setEventTypes(Set.of("payment.succeeded"));
        request.setEnabled(false);

        WebhookEndpointResponseDTO dto = WebhookEndpointResponseDTO.builder()
                .id(id)
                .name("Updated")
                .url("https://example.com/new")
                .eventTypes(Set.of("payment.succeeded"))
                .enabled(false)
                .build();
        when(webhookAdminService.updateEndpoint(eq(id), any())).thenReturn(dto);

        ResponseEntity<WebhookEndpointResponseDTO> response = adminWebhooksAPI.updateWebhook(id, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Updated", response.getBody().getName());
        assertFalse(response.getBody().getEnabled());
        verify(webhookAdminService).updateEndpoint(id, request);
    }

    @Test
    void rotateSecret() {
        UUID id = UUID.randomUUID();
        WebhookSecretResponseDTO response = WebhookSecretResponseDTO.builder()
                .id(id)
                .signingSecret("new-secret")
                .build();
        when(webhookAdminService.rotateSecret(id)).thenReturn(response);

        ResponseEntity<WebhookSecretResponseDTO> result = adminWebhooksAPI.rotateSecret(id);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals("new-secret", result.getBody().getSigningSecret());
        verify(webhookAdminService).rotateSecret(id);
    }

    @Test
    void sendTestEvent() {
        UUID id = UUID.randomUUID();

        ResponseEntity<Void> response = adminWebhooksAPI.sendTestEvent(id);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        verify(webhookAdminService).sendTestEvent(id);
    }
}
