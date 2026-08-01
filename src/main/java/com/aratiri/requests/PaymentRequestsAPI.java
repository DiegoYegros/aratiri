package com.aratiri.requests;

import com.aratiri.errors.ApplicationException;
import com.aratiri.infrastructure.web.context.AratiriContext;
import com.aratiri.infrastructure.web.context.AratiriCtx;
import com.aratiri.requests.application.dto.CreatePaymentRequestDTO;
import com.aratiri.requests.application.dto.OwnerPaymentRequestDTO;
import com.aratiri.requests.application.dto.PaymentRequestPageResponse;
import com.aratiri.requests.application.port.in.PaymentRequestsPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.regex.Pattern;

@RestController
@RequestMapping("/v1/payment-requests")
@Tag(name = "Payment Requests", description = "Shareable single-use fixed-amount Lightning payment links")
public class PaymentRequestsAPI {

    private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 255;
    private static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile("^[\\x21-\\x7E]+$");

    private final PaymentRequestsPort paymentRequestsPort;

    public PaymentRequestsAPI(PaymentRequestsPort paymentRequestsPort) {
        this.paymentRequestsPort = paymentRequestsPort;
    }

    @PostMapping
    @Operation(summary = "Create a payment request", description = "Creates a single-use Lightning payment link for a fixed satoshi amount. Requires Idempotency-Key.")
    public ResponseEntity<OwnerPaymentRequestDTO> create(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequestDTO request,
            @AratiriCtx AratiriContext ctx
    ) {
        validateIdempotencyKey(idempotencyKey);
        OwnerPaymentRequestDTO response = paymentRequestsPort.create(ctx.user().getId(), idempotencyKey, request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping
    @Operation(summary = "List payment requests", description = "Cursor-paginated list of the authenticated user's payment requests.")
    public ResponseEntity<PaymentRequestPageResponse> list(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int limit,
            @AratiriCtx AratiriContext ctx
    ) {
        if (cursor != null && !cursor.isEmpty()) {
            validateCursor(cursor);
        }
        return ResponseEntity.ok(paymentRequestsPort.listOwned(ctx.user().getId(), cursor, limit));
    }

    @GetMapping("/{publicId}")
    @Operation(summary = "Get payment request", description = "Returns a payment request owned by the authenticated user.")
    public ResponseEntity<OwnerPaymentRequestDTO> get(
            @PathVariable String publicId,
            @AratiriCtx AratiriContext ctx
    ) {
        return ResponseEntity.ok(paymentRequestsPort.getOwned(ctx.user().getId(), publicId));
    }

    @PostMapping("/{publicId}/cancel")
    @Operation(
            summary = "Cancel payment request",
            description = "Cancels a payable payment request. Idempotent when already cancelled. "
                    + "If settlement already credited the linked invoice, cancellation is rejected."
    )
    public ResponseEntity<OwnerPaymentRequestDTO> cancel(
            @PathVariable String publicId,
            @AratiriCtx AratiriContext ctx
    ) {
        return ResponseEntity.ok(paymentRequestsPort.cancel(ctx.user().getId(), publicId));
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ApplicationException("Idempotency-Key header is required", HttpStatus.BAD_REQUEST.value());
        }
        if (idempotencyKey.length() > IDEMPOTENCY_KEY_MAX_LENGTH) {
            throw new ApplicationException(
                    "Idempotency-Key must be at most 255 characters",
                    HttpStatus.BAD_REQUEST.value()
            );
        }
        if (!IDEMPOTENCY_KEY_PATTERN.matcher(idempotencyKey).matches()) {
            throw new ApplicationException(
                    "Idempotency-Key must contain only printable ASCII characters without spaces",
                    HttpStatus.BAD_REQUEST.value()
            );
        }
    }

    private void validateCursor(String cursor) {
        try {
            String[] parts = cursor.split("_", 2);
            if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
                throw new ApplicationException("Invalid cursor format. Expected 'timestamp_id'", HttpStatus.BAD_REQUEST.value());
            }
            Long.parseLong(parts[0]);
        } catch (NumberFormatException _) {
            throw new ApplicationException("Invalid cursor format: timestamp must be a valid number", HttpStatus.BAD_REQUEST.value());
        }
    }
}
