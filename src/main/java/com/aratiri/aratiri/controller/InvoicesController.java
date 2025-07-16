package com.aratiri.aratiri.controller;

import com.aratiri.aratiri.context.AratiriContext;
import com.aratiri.aratiri.context.AratiriCtx;
import com.aratiri.aratiri.dto.invoices.DecodedInvoicetDTO;
import com.aratiri.aratiri.dto.invoices.GenerateInvoiceDTO;
import com.aratiri.aratiri.dto.invoices.GenerateInvoiceRequestDTO;
import com.aratiri.aratiri.service.InvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/invoices")
@Tag(name = "Invoices", description = "Invoice management endpoints for generating and decoding Bitcoin Lightning payment requests")
public class InvoicesController {
    private final InvoiceService invoiceService;

    public InvoicesController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @PostMapping
    @Operation(
            summary = "Generate Lightning invoice",
            description = "Creates a new Lightning Network payment request (invoice) for the authenticated user. " +
                    "The invoice can be paid by any Lightning-compatible wallet. The generated invoice includes " +
                    "the specified amount in satoshis and an optional memo description. The invoice expires."
    )
    public ResponseEntity<GenerateInvoiceDTO> generateInvoice(@Valid @RequestBody GenerateInvoiceRequestDTO request, @AratiriCtx AratiriContext aratiriContext) {
        String userId = aratiriContext.getUser().getId();
        return new ResponseEntity<>(invoiceService.generateInvoice(request.getSatsAmount(), request.getMemo(), userId), HttpStatus.CREATED);
    }

    @GetMapping("/invoice/decode/{paymentRequest}")
    @Operation(
            summary = "Decode Lightning payment request",
            description = "Decodes a Lightning Network payment request (invoice) to extract payment details " +
                    "such as amount, destination, expiration time, and description. This endpoint is useful " +
                    "for validating invoices before payment or displaying payment details to users."
    )
    public ResponseEntity<DecodedInvoicetDTO> getDecodedInvoice(@PathVariable String paymentRequest, @AratiriCtx AratiriContext ctx) {
        String userId = ctx.getUser().getId();
        return new ResponseEntity<>(invoiceService.decodeAratiriPaymentRequest(paymentRequest, userId), HttpStatus.OK);
    }
}