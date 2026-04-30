package com.aratiri.admin;

import com.aratiri.infrastructure.persistence.jpa.entity.WebhookDeliveryStatus;
import com.aratiri.webhooks.application.WebhookAdminService;
import com.aratiri.webhooks.application.dto.WebhookDeliveryResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminWebhookDeliveriesAPITest {

    @Mock
    private WebhookAdminService webhookAdminService;

    private AdminWebhookDeliveriesAPI api;

    @BeforeEach
    void setUp() {
        api = new AdminWebhookDeliveriesAPI(webhookAdminService);
    }

    @Test
    void listDeliveries_withAllFilters() {
        UUID endpointId = UUID.randomUUID();
        when(webhookAdminService.listDeliveries(
                endpointId, WebhookDeliveryStatus.FAILED, "payment.succeeded", 50))
                .thenReturn(List.of());

        ResponseEntity<List<WebhookDeliveryResponseDTO>> response = api.listDeliveries(
                endpointId, WebhookDeliveryStatus.FAILED, "payment.succeeded", 50);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        verify(webhookAdminService).listDeliveries(endpointId, WebhookDeliveryStatus.FAILED, "payment.succeeded", 50);
    }

    @Test
    void listDeliveries_defaults() {
        when(webhookAdminService.listDeliveries(null, null, null, 100))
                .thenReturn(List.of());

        ResponseEntity<List<WebhookDeliveryResponseDTO>> response = api.listDeliveries(null, null, null, 100);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(webhookAdminService).listDeliveries(null, null, null, 100);
    }

    @Test
    void getDelivery() {
        UUID id = UUID.randomUUID();
        WebhookDeliveryResponseDTO dto = WebhookDeliveryResponseDTO.builder()
                .id(id)
                .status("SUCCEEDED")
                .attemptCount(1)
                .createdAt(Instant.now())
                .build();
        when(webhookAdminService.getDelivery(id)).thenReturn(dto);

        ResponseEntity<WebhookDeliveryResponseDTO> response = api.getDelivery(id);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("SUCCEEDED", response.getBody().getStatus());
        assertEquals(1, response.getBody().getAttemptCount());
    }

    @Test
    void retryDelivery() {
        UUID id = UUID.randomUUID();

        ResponseEntity<Void> response = api.retryDelivery(id);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        verify(webhookAdminService).retryDelivery(id);
    }
}
