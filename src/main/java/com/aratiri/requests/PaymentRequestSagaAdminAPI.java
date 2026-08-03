package com.aratiri.requests;

import com.aratiri.infrastructure.configuration.PaymentRequestSagaProperties;
import com.aratiri.requests.application.dto.PaymentRequestSagaStatusDTO;
import com.aratiri.requests.application.dto.PaymentRequestSagaWorkItemDTO;
import com.aratiri.requests.application.port.out.PaymentRequestPersistencePort;
import com.aratiri.requests.domain.PaymentRequest;
import com.aratiri.requests.domain.exception.PaymentRequestConflictException;
import com.aratiri.requests.domain.exception.PaymentRequestNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/v1/admin/payment-request-sagas")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Tag(name = "Admin Payment Request Sagas", description = "Inspect durable payment-request provisioning/cancellation work")
public class PaymentRequestSagaAdminAPI {

    private final PaymentRequestPersistencePort persistencePort;
    private final PaymentRequestSagaProperties properties;
    private final Clock clock;

    public PaymentRequestSagaAdminAPI(
            PaymentRequestPersistencePort persistencePort,
            PaymentRequestSagaProperties properties,
            Clock clock
    ) {
        this.persistencePort = persistencePort;
        this.properties = properties;
        this.clock = clock;
    }

    @GetMapping("/status")
    @Operation(summary = "Payment request saga queue counts")
    public ResponseEntity<PaymentRequestSagaStatusDTO> status() {
        var now = clock.instant();
        return ResponseEntity.ok(PaymentRequestSagaStatusDTO.builder()
                .provisioningDue(persistencePort.countDueProvisioning(now))
                .provisioningInProgress(persistencePort.countInProgressProvisioning(now))
                .provisioningFailed(persistencePort.countFailedProvisioning())
                .cancellationDue(persistencePort.countDueCancellations(now))
                .cancellationInProgress(persistencePort.countInProgressCancellations(now))
                .cancellationExhausted(persistencePort.countExhaustedCancellations(properties.getCancelMaxAttempts()))
                .build());
    }

    @GetMapping("/failed")
    @Operation(summary = "List FAILED provisioning requests (inspectable for operators)")
    public ResponseEntity<List<PaymentRequestSagaWorkItemDTO>> failed(
            @RequestParam(defaultValue = "100") int limit
    ) {
        int effective = Math.clamp(limit, 1, 500);
        return ResponseEntity.ok(persistencePort.findFailed(effective).stream().map(this::toItem).toList());
    }

    @GetMapping("/exhausted-cancellations")
    @Operation(summary = "List CANCEL_PENDING rows at or above max attempts")
    public ResponseEntity<List<PaymentRequestSagaWorkItemDTO>> exhaustedCancellations(
            @RequestParam(defaultValue = "100") int limit
    ) {
        int effective = Math.clamp(limit, 1, 500);
        return ResponseEntity.ok(persistencePort.findExhaustedCancellations(
                properties.getCancelMaxAttempts(),
                effective
        ).stream().map(this::toItem).toList());
    }

    @PostMapping("/failed/{publicId}/retry")
    @Operation(summary = "Re-queue a FAILED provisioning request for another attempt")
    public ResponseEntity<PaymentRequestSagaWorkItemDTO> retryFailed(@PathVariable String publicId) {
        Instant now = clock.instant();
        int updated = persistencePort.requeueFailedProvisioning(publicId, now);
        if (updated == 0) {
            PaymentRequest current = persistencePort.findByPublicId(publicId)
                    .orElseThrow(() -> new PaymentRequestNotFoundException("Payment request not found"));
            throw new PaymentRequestConflictException(
                    "Only FAILED payment requests can be re-queued for provisioning (status="
                            + current.storedStatus() + ")"
            );
        }
        PaymentRequest requeued = persistencePort.findByPublicId(publicId)
                .orElseThrow(() -> new PaymentRequestNotFoundException("Payment request not found"));
        return ResponseEntity.accepted().body(toItem(requeued));
    }

    private PaymentRequestSagaWorkItemDTO toItem(PaymentRequest request) {
        return PaymentRequestSagaWorkItemDTO.builder()
                .publicId(request.publicId())
                .status(request.storedStatus().name())
                .paymentHash(request.paymentHash())
                .provisionAttemptCount(request.provisionAttemptCount())
                .provisionLastError(request.provisionLastError())
                .cancelAttemptCount(request.cancelAttemptCount())
                .cancelLastError(request.cancelLastError())
                .createdAt(request.createdAt().toString())
                .expiresAt(request.expiresAt().toString())
                .build();
    }
}
