package com.aratiri.aratiri.controller;

import com.aratiri.aratiri.context.AratiriContext;
import com.aratiri.aratiri.context.AratiriCtx;
import com.aratiri.aratiri.service.CurrencyConversionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/general-data")
@Tag(name = "General Data", description = "General Information.")
public class GemeralDataController {
    private final CurrencyConversionService currencyConversionService;

    public GemeralDataController(CurrencyConversionService currencyConversionService) {
        this.currencyConversionService = currencyConversionService;
    }

    @GetMapping("/currencies")
    public ResponseEntity<List<String>> getFiatCurrencies(@AratiriCtx AratiriContext ctx) {
        return ResponseEntity.ok(currencyConversionService.getFiatCurrencies());
    }
}
