package com.aratiri.aratiri.controller;

import com.aratiri.aratiri.context.AratiriContext;
import com.aratiri.aratiri.context.AratiriCtx;
import com.aratiri.aratiri.dto.invoices.DecodedInvoicetDTO;
import com.aratiri.aratiri.dto.invoices.GenerateInvoiceDTO;
import com.aratiri.aratiri.dto.invoices.GenerateInvoiceRequestDTO;
import com.aratiri.aratiri.service.InvoiceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/invoices")
public class InvoicesController {
    private final InvoiceService invoiceService;

    public InvoicesController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @PostMapping
    public ResponseEntity<GenerateInvoiceDTO> generateInvoice(@Valid @RequestBody GenerateInvoiceRequestDTO request, @AratiriCtx AratiriContext aratiriContext) {
        String userId = aratiriContext.getUser().getId();
        return new ResponseEntity<>(invoiceService.generateInvoice(request.getSatsAmount(), request.getMemo(), userId), HttpStatus.CREATED);
    }

    @GetMapping("/invoice/decode/{paymentRequest}")
    public ResponseEntity<DecodedInvoicetDTO> getDecodedInvoice(@PathVariable String paymentRequest, @AratiriCtx AratiriContext ctx) {
        String userId = ctx.getUser().getId();
        return new ResponseEntity<>(invoiceService.decodePaymentRequest(paymentRequest, userId), HttpStatus.OK);
    }
}