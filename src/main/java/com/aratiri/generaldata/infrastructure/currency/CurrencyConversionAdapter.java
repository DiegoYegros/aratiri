package com.aratiri.generaldata.infrastructure.currency;

import com.aratiri.generaldata.domain.BtcPricePoint;
import com.aratiri.generaldata.domain.BtcPriceRange;
import com.aratiri.generaldata.application.port.out.CurrencyConversionPort;
import com.aratiri.infrastructure.configuration.AratiriProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class CurrencyConversionAdapter implements CurrencyConversionPort {

    private static final Logger logger = LoggerFactory.getLogger(CurrencyConversionAdapter.class);

    private final RestTemplate restTemplate;
    private final JsonMapper jsonMapper;
    private final AratiriProperties aratiriProperties;
    private final Clock clock;

    public CurrencyConversionAdapter(RestTemplate restTemplate, JsonMapper jsonMapper, AratiriProperties aratiriProperties, Clock clock) {
        this.restTemplate = restTemplate;
        this.jsonMapper = jsonMapper;
        this.aratiriProperties = aratiriProperties;
        this.clock = clock;
    }

    @Override
    public Map<String, BigDecimal> getCurrentBtcPrice(List<String> currencies) {
        Map<String, BigDecimal> prices = new HashMap<>();
        currencies.forEach(currency -> {
            logger.info("Fetching current BTC price in {} from Currency Conversion API...", currency.toUpperCase());
            BigDecimal fromCoinGecko = getFromCoinGecko(currency);
            if (fromCoinGecko != null) {
                prices.put(currency, fromCoinGecko);
            } else {
                BigDecimal btcPriceFromFallback = getBtcPriceFromFallback(currency);
                if (btcPriceFromFallback != null) {
                    prices.put(currency, btcPriceFromFallback);
                }
            }
        });
        return prices;
    }

    @Override
    public List<BtcPricePoint> getBtcPriceHistory(String currency, BtcPriceRange range) {
        String apiUrl = String.format(
                aratiriProperties.getCoingeckoMarketChartApiUrlTemplate(),
                currency.toLowerCase(),
                range.coingeckoDays()
        );
        logger.info("Fetching BTC price history in {} for range {} from CoinGecko...", currency.toUpperCase(), range.code());
        try {
            String jsonResponse = restTemplate.getForObject(apiUrl, String.class);
            if (jsonResponse == null) {
                throw new IllegalStateException("No response from CoinGecko market chart API.");
            }
            JsonNode response = jsonMapper.readTree(jsonResponse);
            JsonNode prices = response.path("prices");
            if (!prices.isArray()) {
                throw new IllegalStateException("Invalid market chart response format.");
            }

            Instant cutoff = Instant.now(clock).minus(range.duration());
            List<BtcPricePoint> points = new ArrayList<>();
            for (JsonNode point : prices) {
                if (!point.isArray() || point.size() < 2 || point.get(0).isNull() || point.get(1).isNull()) {
                    continue;
                }
                Instant timestamp = Instant.ofEpochMilli(point.get(0).longValue());
                if (timestamp.isBefore(cutoff)) {
                    continue;
                }
                points.add(new BtcPricePoint(timestamp, point.get(1).decimalValue()));
            }

            if (points.isEmpty()) {
                throw new IllegalStateException("No BTC price history points available.");
            }
            return points;
        } catch (Exception e) {
            logger.error("Failed to fetch BTC price history in {} for range {}", currency.toUpperCase(), range.code(), e);
            throw new IllegalStateException("Failed to fetch BTC price history.", e);
        }
    }

    private BigDecimal getBtcPriceFromFallback(String currency) {
        logger.info("Attempting to fetch BTC price in {} from fallback API...", currency.toUpperCase());
        try {
            String btcConversionsUrl = String.format(aratiriProperties.getFallbackApiUrlTemplate(), "btc");
            String jsonResponse = restTemplate.getForObject(btcConversionsUrl, String.class);
            if (jsonResponse == null) {
                logger.warn("No response from fallback API for BTC rates");
                return null;
            }
            Map<String, Object> response = jsonMapper.readValue(jsonResponse, new TypeReference<>() {
            });
            if (response == null || !response.containsKey("btc")) {
                logger.warn("Invalid response format from fallback API");
                return null;
            }
            Object btcRatesObj = response.get("btc");
            if (!(btcRatesObj instanceof Map)) {
                logger.warn("BTC rates not in expected format from fallback API");
                return null;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> btcRates = (Map<String, Object>) btcRatesObj;
            String targetCurrency = currency.toLowerCase();
            if (!btcRates.containsKey(targetCurrency)) {
                logger.warn("Currency {} not found in fallback API", currency.toUpperCase());
                return null;
            }
            Object rateObj = btcRates.get(targetCurrency);
            if (rateObj == null) {
                logger.warn("Invalid exchange rate for {} from fallback API", currency.toUpperCase());
                return null;
            }
            BigDecimal btcToTargetCurrency;
            if (rateObj instanceof Number) {
                btcToTargetCurrency = BigDecimal.valueOf(((Number) rateObj).doubleValue());
            } else if (rateObj instanceof String) {
                try {
                    btcToTargetCurrency = new BigDecimal((String) rateObj);
                } catch (NumberFormatException e) {
                    logger.warn("Cannot parse exchange rate for {} from fallback API: {}", currency.toUpperCase(), rateObj);
                    return null;
                }
            } else {
                logger.warn("Unexpected exchange rate format for {} from fallback API: {}", currency.toUpperCase(), rateObj.getClass().getSimpleName());
                return null;
            }
            logger.info("Successfully fetched BTC price in {} from fallback API: {}", currency.toUpperCase(), btcToTargetCurrency);
            return btcToTargetCurrency;
        } catch (Exception e) {
            logger.error("Failed to fetch BTC price in {} from fallback API", currency.toUpperCase(), e);
            return null;
        }
    }

    private BigDecimal getFromCoinGecko(String currency) {
        String apiUrl = String.format(aratiriProperties.getCoingeckoApiUrlTemplate(), currency.toLowerCase());
        try {
            String jsonResponse = restTemplate.getForObject(apiUrl, String.class);
            Map<String, Map<String, Double>> response = jsonMapper.readValue(jsonResponse, new TypeReference<>() {
            });
            if (response != null && response.containsKey("bitcoin") && response.get("bitcoin").containsKey(currency.toLowerCase())) {
                Double price = response.get("bitcoin").get(currency.toLowerCase());
                logger.info("Successfully fetched BTC price from CoinGecko. {}: {}", currency.toUpperCase(), price);
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
