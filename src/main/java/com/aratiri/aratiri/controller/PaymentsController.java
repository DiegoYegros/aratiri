package com.aratiri.aratiri.controller;

import com.aratiri.aratiri.context.AratiriContext;
import com.aratiri.aratiri.context.AratiriCtx;
import com.aratiri.aratiri.dto.payments.PayInvoiceRequestDTO;
import com.aratiri.aratiri.dto.payments.PaymentResponseDTO;
import com.aratiri.aratiri.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/payments")
@Tag(name = "Payments", description = "Endpoints for sending Bitcoin Lightning payments")
public class PaymentsController {

    private final PaymentService paymentService;

    public PaymentsController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/invoice")
    @Operation(
            summary = "Pay a Lightning invoice",
            description = "Attempts to pay a Lightning Network invoice (BOLT11). The call initiates the payment attempt and returns immediately. " +
                    "The payment is processed asynchronously. You can query the transaction status later."
    )
    public ResponseEntity<PaymentResponseDTO> payInvoice(
            @Valid @RequestBody PayInvoiceRequestDTO request,
            @AratiriCtx AratiriContext ctx) {
        PaymentResponseDTO response = paymentService.payLightningInvoice(request, ctx.getUser().getId());
        return new ResponseEntity<>(response, HttpStatus.ACCEPTED);
    }
}