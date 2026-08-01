package com.aratiri.requests;

import com.aratiri.requests.application.dto.PublicPaymentRequestDTO;
import com.aratiri.requests.application.port.in.PaymentRequestsPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/r")
@Tag(name = "Public Payment Requests", description = "Unauthenticated lookup of shareable Lightning payment links")
public class PublicPaymentRequestsAPI {

    private final PaymentRequestsPort paymentRequestsPort;

    public PublicPaymentRequestsAPI(PaymentRequestsPort paymentRequestsPort) {
        this.paymentRequestsPort = paymentRequestsPort;
    }

    @GetMapping("/{publicId}")
    @Operation(
            summary = "Get public payment request",
            description = "Returns safe fields for a shareable payment request. BOLT11 is included only while payable."
    )
    public ResponseEntity<PublicPaymentRequestDTO> getPublic(@PathVariable String publicId) {
        // Cache-Control: no-store is applied by PublicPaymentRequestCacheControlFilter for all /r/** outcomes.
        return ResponseEntity.ok(paymentRequestsPort.getPublic(publicId));
    }
}
