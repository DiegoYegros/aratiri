package com.aratiri.webhooks.application.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WebhookDTOsTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void webhookPayload_builder() throws Exception {
        WebhookPayloadData data = WebhookPayloadData.builder()
                .transactionId("tx-1")
                .userId("user-1")
                .build();
        WebhookPayload payload = WebhookPayload.builder()
                .id("evt-1")
                .type("payment.succeeded")
                .createdAt(Instant.now())
                .apiVersion("v1")
                .data(data)
                .build();

        String json = mapper.writeValueAsString(payload);
        assertTrue(json.contains("payment.succeeded"));
        assertTrue(json.contains("created_at"));
        assertTrue(json.contains("api_version"));
    }

    @Test
    void webhookPayloadData_builder() throws Exception {
        WebhookPayloadData data = WebhookPayloadData.builder()
                .transactionId("tx-1")
                .userId("user-1")
                .externalReference("ext-ref")
                .metadata("meta")
                .amountSat(1000L)
                .status("COMPLETED")
                .referenceId("ref-1")
                .balanceAfterSat(5000L)
                .failureReason(null)
                .invoiceId("inv-1")
                .paymentHash("hash")
                .paymentRequest("pr")
                .amountPaidSat(1000L)
                .memo("test")
                .operationId("op-1")
                .operationType("LIGHTNING_PAYMENT")
                .operationStatus("UNKNOWN_OUTCOME")
                .externalId("ext-id-1")
                .attemptCount(5)
                .operationError("error")
                .build();

        assertEquals("tx-1", data.getTransactionId());
        assertEquals("user-1", data.getUserId());
        assertEquals("ext-ref", data.getExternalReference());
        assertEquals("meta", data.getMetadata());
        assertEquals(1000L, data.getAmountSat());
        assertEquals("COMPLETED", data.getStatus());
        assertEquals("ref-1", data.getReferenceId());
        assertEquals(5000L, data.getBalanceAfterSat());
        assertNull(data.getFailureReason());
        String json = mapper.writeValueAsString(data);
        assertTrue(json.contains("transaction_id"));
    }

    @Test
    void createWebhookEndpointRequestDTO_allFields() {
        CreateWebhookEndpointRequestDTO dto = new CreateWebhookEndpointRequestDTO();
        dto.setName("My Webhook");
        dto.setUrl("https://example.com/webhook");
        dto.setEventTypes(java.util.Set.of("payment.succeeded", "invoice.created"));
        dto.setEnabled(false);

        assertEquals("My Webhook", dto.getName());
        assertEquals("https://example.com/webhook", dto.getUrl());
        assertEquals(2, dto.getEventTypes().size());
        assertFalse(dto.getEnabled());
    }

    @Test
    void updateWebhookEndpointRequestDTO_allFields() {
        UpdateWebhookEndpointRequestDTO dto = new UpdateWebhookEndpointRequestDTO();
        dto.setName("Updated");
        dto.setUrl("https://example.com/new");
        dto.setEventTypes(java.util.Set.of("invoice.created"));
        dto.setEnabled(true);

        assertEquals("Updated", dto.getName());
        assertEquals("https://example.com/new", dto.getUrl());
        assertTrue(dto.getEnabled());
    }

    @Test
    void webhookEndpointResponseDTO_builder() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        WebhookEndpointResponseDTO dto = WebhookEndpointResponseDTO.builder()
                .id(id)
                .name("Test")
                .url("https://example.com")
                .eventTypes(java.util.Set.of("payment.succeeded"))
                .enabled(true)
                .createdAt(now)
                .updatedAt(now)
                .lastSuccessAt(now)
                .lastFailureAt(null)
                .build();

        assertEquals(id, dto.getId());
        assertEquals("Test", dto.getName());
        assertTrue(dto.getEnabled());
        assertNull(dto.getLastFailureAt());
    }

    @Test
    void webhookSecretResponseDTO_builder() {
        UUID id = UUID.randomUUID();
        WebhookSecretResponseDTO dto = WebhookSecretResponseDTO.builder()
                .id(id)
                .signingSecret("supersecret")
                .build();

        assertEquals(id, dto.getId());
        assertEquals("supersecret", dto.getSigningSecret());
    }

    @Test
    void webhookDeliveryResponseDTO_builder() {
        UUID id = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();
        WebhookDeliveryResponseDTO dto = WebhookDeliveryResponseDTO.builder()
                .id(id)
                .eventId(eventId)
                .endpointId(UUID.randomUUID())
                .status("SUCCEEDED")
                .attemptCount(1)
                .nextAttemptAt(now)
                .responseStatus(200)
                .lastError(null)
                .createdAt(now)
                .updatedAt(now)
                .deliveredAt(now)
                .eventType("payment.succeeded")
                .build();

        assertEquals(id, dto.getId());
        assertEquals(eventId, dto.getEventId());
        assertEquals("SUCCEEDED", dto.getStatus());
        assertEquals(1, dto.getAttemptCount());
        assertEquals(200, dto.getResponseStatus());
        assertEquals("payment.succeeded", dto.getEventType());
    }
}
