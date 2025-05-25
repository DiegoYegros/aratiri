package com.aratiri.aratiri.controller;

import com.aratiri.aratiri.dto.invoices.GenerateInvoiceDTO;
import com.aratiri.aratiri.dto.invoices.GenerateInvoiceRequest;
import com.aratiri.aratiri.service.InvoiceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/invoices")
public class InvoicesController {
    private final InvoiceService invoiceService;

    public InvoicesController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @PostMapping
    public ResponseEntity<GenerateInvoiceDTO> generateInvoice(@Valid @RequestBody GenerateInvoiceRequest request) {
        return new ResponseEntity<>(invoiceService.generateInvoice(request.getSatsAmount(), request.getMemo()), HttpStatus.CREATED);
    }

}