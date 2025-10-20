package com.aratiri.lnurl;

import com.aratiri.infrastructure.web.context.AratiriContext;
import com.aratiri.infrastructure.web.context.AratiriCtx;
import com.aratiri.lnurl.application.dto.LnurlPayRequestDTO;
import com.aratiri.lnurl.application.dto.LnurlpResponseDTO;
import com.aratiri.lnurl.application.port.in.LnurlApplicationPort;
import com.aratiri.payments.application.dto.PaymentResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "LNURL", description = "Lightning Network URL (LNURL) protocol endpoints for Bitcoin Lightning payments")
public class LnurlAPI {

    private final LnurlApplicationPort lnurlPort;

    public LnurlAPI(LnurlApplicationPort lnurlPort) {
        this.lnurlPort = lnurlPort;
    }

    @GetMapping("/.well-known/lnurlp/{alias}")
    @Operation(
            summary = "Get LNURL-pay metadata",
            description = "Retrieves LNURL-pay metadata for a given user alias. This endpoint provides payment configuration including minimum/maximum amounts and callback URLs. This is the first step in the LNURL-pay flow where wallets discover payment parameters before generating an invoice."
    )
    public ResponseEntity<LnurlpResponseDTO> getLnurlMetadata(@PathVariable String alias) {
        LnurlpResponseDTO response = lnurlPort.getLnurlMetadata(alias);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/lnurl/callback/{alias}")
    @Operation(
            summary = "LNURL-pay callback",
            description = "Handles the LNURL-pay callback request to generate a Lightning invoice. This is the second step in the LNURL-pay flow where the wallet calls this endpoint with the payment amount (and optional comment) to receive a Lightning invoice. The generated invoice is then paid by the wallet to complete the payment. This endpoint creates the actual Lightning invoice through the connected Lightning node."
    )
    public ResponseEntity<?> lnurlCallback(
            @PathVariable String alias,
            @RequestParam long amount,
            @RequestParam(required = false) String comment
    ) {
        Object response = lnurlPort.lnurlCallback(alias, amount, comment);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/v1/lnurl/pay")
    @Operation(
            summary = "Execute LNURL-pay request",
            description = "Handles the final step of an LNURL-pay flow. The backend fetches the invoice from the provided callback URL and then pays it."
    )
    public ResponseEntity<PaymentResponseDTO> pay(
            @Valid @RequestBody LnurlPayRequestDTO request,
            @AratiriCtx AratiriContext ctx) {
        PaymentResponseDTO response = lnurlPort.handlePayRequest(request, ctx.user().getId());
        return new ResponseEntity<>(response, HttpStatus.ACCEPTED);
    }
}
