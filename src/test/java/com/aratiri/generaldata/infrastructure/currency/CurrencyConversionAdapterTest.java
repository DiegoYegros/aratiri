package com.aratiri.generaldata.infrastructure.currency;

import com.aratiri.infrastructure.configuration.AratiriProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrencyConversionAdapterTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private AratiriProperties aratiriProperties;

    private CurrencyConversionAdapter currencyConversionAdapter;

    @BeforeEach
    void setUp() {
        JsonMapper jsonMapper = new JsonMapper();
        currencyConversionAdapter = new CurrencyConversionAdapter(restTemplate, jsonMapper, aratiriProperties);
    }

    @Test
    void getCurrentBtcPrice_shouldReturnPriceFromCoinGecko() {
        String coingeckoResponse = "{\"bitcoin\":{\"usd\":50000.0}}";

        when(aratiriProperties.getCoingeckoApiUrlTemplate())
                .thenReturn("https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=%s");
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(coingeckoResponse);

        Map<String, BigDecimal> result = currencyConversionAdapter.getCurrentBtcPrice(List.of("usd"));

        assertNotNull(result);
        assertEquals(BigDecimal.valueOf(50000.0), result.get("usd"));
    }

    @Test
    void getCurrentBtcPrice_shouldFallbackWhenCoinGeckoFails() {
        String fallbackResponse = "{\"btc\":{\"usd\":49000}}";

        when(aratiriProperties.getCoingeckoApiUrlTemplate())
                .thenReturn("https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=%s");
        when(aratiriProperties.getFallbackApiUrlTemplate())
                .thenReturn("https://fallback.api/%s");
        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenReturn(null)
                .thenReturn(fallbackResponse);

        Map<String, BigDecimal> result = currencyConversionAdapter.getCurrentBtcPrice(List.of("usd"));

        assertNotNull(result);
        assertEquals(BigDecimal.valueOf(49000.0), result.get("usd"));
    }

    @Test
    void getCurrentBtcPrice_shouldHandleMultipleCurrencies() {
        String usdResponse = "{\"bitcoin\":{\"usd\":50000.0}}";
        String eurResponse = "{\"bitcoin\":{\"eur\":45000.0}}";

        when(aratiriProperties.getCoingeckoApiUrlTemplate())
                .thenReturn("https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=%s");
        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenReturn(usdResponse)
                .thenReturn(eurResponse);

        Map<String, BigDecimal> result = currencyConversionAdapter.getCurrentBtcPrice(List.of("usd", "eur"));

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(BigDecimal.valueOf(50000.0), result.get("usd"));
        assertEquals(BigDecimal.valueOf(45000.0), result.get("eur"));
    }

    @Test
    void getCurrentBtcPrice_shouldHandleExceptionGracefully() {
        when(aratiriProperties.getCoingeckoApiUrlTemplate())
                .thenReturn("https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=%s");
        when(aratiriProperties.getFallbackApiUrlTemplate())
                .thenReturn("https://fallback.api/%s");
        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenThrow(new RuntimeException("Network error"));

        Map<String, BigDecimal> result = currencyConversionAdapter.getCurrentBtcPrice(List.of("usd"));

        assertNotNull(result);
        assertNull(result.get("usd"));
    }
}
