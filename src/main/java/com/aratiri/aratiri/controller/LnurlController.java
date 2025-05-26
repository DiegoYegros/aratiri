package com.aratiri.aratiri.controller;

import com.aratiri.aratiri.service.LnurlService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping
public class LnurlController {

    private final LnurlService lnurlService;

    public LnurlController(LnurlService lnurlService) {
        this.lnurlService = lnurlService;
    }

    @GetMapping("/.well-known/lnurlp/{alias}")
    public ResponseEntity<?> getLnurlMetadata(@PathVariable String alias) {
        Object response = lnurlService.getLnurlMetadata(alias);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/lnurl/callback/{alias}")
    public ResponseEntity<?> lnurlCallback(
            @PathVariable String alias,
            @RequestParam long amount,
            @RequestParam(required = false) String comment
    ) {
        Object response = lnurlService.lnurlCallback(alias, amount, comment);
        return ResponseEntity.ok(response);
    }

}