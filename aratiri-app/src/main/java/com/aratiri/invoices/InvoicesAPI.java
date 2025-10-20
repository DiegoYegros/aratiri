package com.aratiri.invoices;

import com.aratiri.infrastructure.web.context.AratiriContext;
import com.aratiri.infrastructure.web.context.AratiriCtx;
import com.aratiri.dto.invoices.DecodedInvoicetDTO;
import com.aratiri.dto.invoices.GenerateInvoiceDTO;
import com.aratiri.dto.invoices.GenerateInvoiceRequestDTO;
import com.aratiri.invoices.application.port.in.InvoicesPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/invoices")
@Tag(name = "Invoices", description = "Invoice management endpoints for generating and decoding Bitcoin Lightning payment requests")
public class InvoicesAPI {
    private final InvoicesPort invoicesPort;

    public InvoicesAPI(InvoicesPort invoicesPort) {
        this.invoicesPort = invoicesPort;
    }

    @PostMapping
    @Operation(
            summary = "Generate Lightning invoice",
            description = "Creates a new Lightning Network payment request (invoice) for the authenticated user. " +
                    "The invoice can be paid by any Lightning-compatible wallet. The generated invoice includes " +
                    "the specified amount in satoshis and an optional memo description. The invoice expires."
    )
    public ResponseEntity<GenerateInvoiceDTO> generateInvoice(@Valid @RequestBody GenerateInvoiceRequestDTO request, @AratiriCtx AratiriContext aratiriContext) {
        String userId = aratiriContext.user().getId();
        return new ResponseEntity<>(invoicesPort.generateInvoice(request.getSatsAmount(), request.getMemo(), userId), HttpStatus.CREATED);
    }

    @GetMapping("/invoice/decode/{paymentRequest}")
    @Operation(
            summary = "Decode Lightning payment request",
            description = "Decodes a Lightning Network payment request (invoice) to extract payment details " +
                    "such as amount, destination, expiration time, and description. This endpoint is useful " +
                    "for validating invoices before payment or displaying payment details to users."
    )
    public ResponseEntity<DecodedInvoicetDTO> getDecodedInvoice(@PathVariable String paymentRequest, @AratiriCtx AratiriContext ctx) {
        String userId = ctx.user().getId();
        return new ResponseEntity<>(invoicesPort.decodeAratiriPaymentRequest(paymentRequest, userId), HttpStatus.OK);
    }
}