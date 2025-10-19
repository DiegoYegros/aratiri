package com.aratiri.controller;

import com.aratiri.context.AratiriContext;
import com.aratiri.context.AratiriCtx;
import com.aratiri.service.CurrencyConversionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/general-data")
@Tag(name = "General Data", description = "General Information.")
public class GeneralDataController {
    private final CurrencyConversionService currencyConversionService;

    public GeneralDataController(CurrencyConversionService currencyConversionService) {
        this.currencyConversionService = currencyConversionService;
    }

    @GetMapping("/currencies")
    public ResponseEntity<List<String>> getFiatCurrencies(@AratiriCtx AratiriContext ctx) {
        return ResponseEntity.ok(currencyConversionService.getFiatCurrencies());
    }
}
