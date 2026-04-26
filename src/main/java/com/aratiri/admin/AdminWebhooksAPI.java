package com.aratiri.admin;

import com.aratiri.webhooks.application.WebhookAdminService;
import com.aratiri.webhooks.application.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/admin/webhooks")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Tag(name = "Admin Webhooks", description = "Admin-managed webhook endpoints for institutional integrators")
@RequiredArgsConstructor
public class AdminWebhooksAPI {

    private final WebhookAdminService webhookAdminService;

    @PostMapping
    @Operation(summary = "Create a new webhook endpoint")
    public ResponseEntity<WebhookSecretResponseDTO> createWebhook(@Valid @RequestBody CreateWebhookEndpointRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(webhookAdminService.createEndpoint(request));
    }

    @GetMapping
    @Operation(summary = "List all webhook endpoints")
    public ResponseEntity<List<WebhookEndpointResponseDTO>> listWebhooks() {
        return ResponseEntity.ok(webhookAdminService.listEndpoints());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a webhook endpoint by ID")
    public ResponseEntity<WebhookEndpointResponseDTO> getWebhook(@PathVariable UUID id) {
        return ResponseEntity.ok(webhookAdminService.getEndpoint(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a webhook endpoint")
    public ResponseEntity<WebhookEndpointResponseDTO> updateWebhook(@PathVariable UUID id, @Valid @RequestBody UpdateWebhookEndpointRequestDTO request) {
        return ResponseEntity.ok(webhookAdminService.updateEndpoint(id, request));
    }

    @PostMapping("/{id}/rotate-secret")
    @Operation(summary = "Rotate the signing secret for a webhook endpoint")
    public ResponseEntity<WebhookSecretResponseDTO> rotateSecret(@PathVariable UUID id) {
        return ResponseEntity.ok(webhookAdminService.rotateSecret(id));
    }

    @PostMapping("/{id}/test")
    @Operation(summary = "Send a test event to a webhook endpoint")
    public ResponseEntity<Void> sendTestEvent(@PathVariable UUID id) {
        webhookAdminService.sendTestEvent(id);
        return ResponseEntity.accepted().build();
    }

}
