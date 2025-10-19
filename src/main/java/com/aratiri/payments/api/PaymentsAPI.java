package com.aratiri.payments.api;

import com.aratiri.context.AratiriContext;
import com.aratiri.context.AratiriCtx;
import com.aratiri.dto.payments.OnChainPaymentDTOs;
import com.aratiri.dto.payments.PayInvoiceRequestDTO;
import com.aratiri.dto.payments.PaymentResponseDTO;
import com.aratiri.payments.application.port.in.PaymentPort;
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
public class PaymentsAPI {

    private final PaymentPort paymentPort;

    public PaymentsAPI(PaymentPort paymentPort) {
        this.paymentPort = paymentPort;
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
        PaymentResponseDTO response = paymentPort.payLightningInvoice(request, ctx.user().getId());
        return new ResponseEntity<>(response, HttpStatus.ACCEPTED);
    }

    @PostMapping("/onchain")
    @Operation(
            summary = "Send Bitcoin on-chain",
            description = "Creates and broadcasts a Bitcoin transaction to a specified address. " +
                    "This endpoint handles the user's internal balance deduction and interaction with the LND node."
    )
    public ResponseEntity<OnChainPaymentDTOs.SendOnChainResponseDTO> sendOnChain(
            @Valid @RequestBody OnChainPaymentDTOs.SendOnChainRequestDTO request,
            @AratiriCtx AratiriContext ctx) {
        OnChainPaymentDTOs.SendOnChainResponseDTO response = paymentPort.sendOnChain(request, ctx.user().getId());
        return new ResponseEntity<>(response, HttpStatus.ACCEPTED);
    }

    @PostMapping("/onchain/estimate-fee")
    @Operation(
            summary = "Estimate on-chain fee",
            description = "Estimates the fee for a Bitcoin on-chain transaction."
    )
    public ResponseEntity<OnChainPaymentDTOs.EstimateFeeResponseDTO> estimateOnChainFee(
            @Valid @RequestBody OnChainPaymentDTOs.EstimateFeeRequestDTO request,
            @AratiriCtx AratiriContext ctx) {
        OnChainPaymentDTOs.EstimateFeeResponseDTO response = paymentPort.estimateOnChainFee(request, ctx.user().getId());
        return ResponseEntity.ok(response);
    }
}