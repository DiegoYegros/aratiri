package com.aratiri.admin;

import com.aratiri.infrastructure.persistence.jpa.entity.WebhookDeliveryStatus;
import com.aratiri.webhooks.application.WebhookAdminService;
import com.aratiri.webhooks.application.dto.WebhookDeliveryResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/admin/webhook-deliveries")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Tag(name = "Admin Webhook Deliveries", description = "Webhook delivery monitoring and retry")
@RequiredArgsConstructor
public class AdminWebhookDeliveriesAPI {

    private final WebhookAdminService webhookAdminService;

    @GetMapping
    @Operation(summary = "List webhook deliveries with optional filters")
    public ResponseEntity<List<WebhookDeliveryResponseDTO>> listDeliveries(
            @RequestParam(required = false) UUID endpointId,
            @RequestParam(required = false) WebhookDeliveryStatus status,
            @RequestParam(required = false) String eventType,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return ResponseEntity.ok(webhookAdminService.listDeliveries(endpointId, status, eventType, limit));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a webhook delivery by ID")
    public ResponseEntity<WebhookDeliveryResponseDTO> getDelivery(@PathVariable UUID id) {
        return ResponseEntity.ok(webhookAdminService.getDelivery(id));
    }

    @PostMapping("/{id}/retry")
    @Operation(summary = "Retry a failed or pending webhook delivery")
    public ResponseEntity<Void> retryDelivery(@PathVariable UUID id) {
        webhookAdminService.retryDelivery(id);
        return ResponseEntity.accepted().build();
    }
}
