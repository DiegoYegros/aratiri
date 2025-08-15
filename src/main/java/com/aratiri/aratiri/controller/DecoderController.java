package com.aratiri.aratiri.controller;

import com.aratiri.aratiri.dto.decoder.DecodedResultDTO;
import com.aratiri.aratiri.service.impl.DecoderServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/decoder")
@Tag(name = "Decoder", description = "Decodes lnurl, Lightning Invoices, on-chain invoices, aliases")
public class DecoderController {

    private final DecoderServiceImpl decoderService;

    public DecoderController(DecoderServiceImpl decoderService) {
        this.decoderService = decoderService;
    }

    @GetMapping
    @Operation(summary = "Decode a string", description = "Decodes a string that can be a Lightning Invoice, LNURL, Bitcoin Address or alias.")
    public ResponseEntity<DecodedResultDTO> decode(@RequestParam String input) {
        return ResponseEntity.ok(decoderService.decode(input));
    }
}