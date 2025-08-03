package com.aratiri.aratiri.service.impl;

import com.aratiri.aratiri.service.CurrencyConversionService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;

@Service
public class CurrencyConversionServiceImpl implements CurrencyConversionService {

    private static final Logger logger = LoggerFactory.getLogger(CurrencyConversionServiceImpl.class);
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    @Value("${aratiri.currency.conversion.api.url:https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=%s}")

    private String coingeckoApiUrlTemplate;

    public CurrencyConversionServiceImpl(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Cacheable(value = "btcPrice", key = "#currency")
    public BigDecimal getCurrentBtcPrice(String currency) {
        logger.info("Fetching current BTC price in {} from Currency Conversion API...", currency.toUpperCase());
        String apiUrl = String.format(coingeckoApiUrlTemplate, currency.toLowerCase());
        try {
            String jsonResponse = restTemplate.getForObject(apiUrl, String.class);
            Map<String, Map<String, Double>> response = objectMapper.readValue(jsonResponse, new TypeReference<>() {
            });
            if (response != null && response.containsKey("bitcoin") && response.get("bitcoin").containsKey(currency.toLowerCase())) {
                Double price = response.get("bitcoin").get(currency.toLowerCase());
                logger.info("Successfully fetched BTC price in {}: {}", currency.toUpperCase(), price);
                return BigDecimal.valueOf(price);
            } else {
                logger.warn("Invalid response format or currency not found from Currency Conversion API for: {}", currency);
                return null;
            }
        } catch (Exception e) {
            logger.error("Failed to fetch BTC price in {} from Currency Conversion API. Returning null.", currency.toUpperCase(), e);
            return null;
        }
    }
}
