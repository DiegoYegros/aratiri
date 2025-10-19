package com.aratiri.decoder.api;

import com.aratiri.decoder.api.dto.DecodedResultDTO;
import com.aratiri.decoder.application.port.in.DecoderPort;
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
public class DecoderAPI {

    private final DecoderPort decoderPort;

    public DecoderAPI(DecoderPort decoderPort) {
        this.decoderPort = decoderPort;
    }

    @GetMapping
    @Operation(summary = "Decode a string", description = "Decodes a string that can be a Lightning Invoice, LNURL, Bitcoin Address or alias.")
    public ResponseEntity<DecodedResultDTO> decode(@RequestParam String input) {
        return ResponseEntity.ok(decoderPort.decode(input));
    }
}