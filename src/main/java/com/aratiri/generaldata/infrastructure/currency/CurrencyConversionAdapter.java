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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
public class CurrencyConversionAdapter implements CurrencyConversionPort {

    private static final Logger logger = LoggerFactory.getLogger(CurrencyConversionAdapter.class);
    private static final String BITCOIN_ID = "bitcoin";
    private static final String BTC_ID = "btc";

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
            String currencyCode = currency.toUpperCase(Locale.ROOT);
            logger.info("Fetching current BTC price in {} from Currency Conversion API...", currencyCode);
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
        String currencyCode = currency.toUpperCase(Locale.ROOT);
        String apiUrl = String.format(
                aratiriProperties.getCoingeckoMarketChartApiUrlTemplate(),
                currency.toLowerCase(Locale.ROOT),
                range.coingeckoDays()
        );
        logger.info("Fetching BTC price history in {} for range {} from CoinGecko...", currencyCode, range.code());
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
                toPricePoint(point, cutoff).ifPresent(points::add);
            }

            if (points.isEmpty()) {
                throw new IllegalStateException("No BTC price history points available.");
            }
            return points;
        } catch (Exception e) {
            throw new IllegalStateException(
                    String.format("Failed to fetch BTC price history in %s for range %s.", currencyCode, range.code()),
                    e
            );
        }
    }

    private Optional<BtcPricePoint> toPricePoint(JsonNode point, Instant cutoff) {
        if (!point.isArray() || point.size() < 2 || point.get(0).isNull() || point.get(1).isNull()) {
            return Optional.empty();
        }
        Instant timestamp = Instant.ofEpochMilli(point.get(0).longValue());
        if (timestamp.isBefore(cutoff)) {
            return Optional.empty();
        }
        return Optional.of(new BtcPricePoint(timestamp, point.get(1).decimalValue()));
    }

    private BigDecimal getBtcPriceFromFallback(String currency) {
        String currencyCode = currency.toUpperCase(Locale.ROOT);
        logger.info("Attempting to fetch BTC price in {} from fallback API...", currencyCode);
        try {
            String btcConversionsUrl = String.format(aratiriProperties.getFallbackApiUrlTemplate(), BTC_ID);
            String jsonResponse = restTemplate.getForObject(btcConversionsUrl, String.class);
            if (jsonResponse == null) {
                logger.warn("No response from fallback API for BTC rates");
                return null;
            }
            Map<String, Object> response = jsonMapper.readValue(jsonResponse, new TypeReference<>() {
            });
            if (response == null || !response.containsKey(BTC_ID)) {
                logger.warn("Invalid response format from fallback API");
                return null;
            }
            Object btcRatesObj = response.get(BTC_ID);
            if (!(btcRatesObj instanceof Map<?, ?>)) {
                logger.warn("BTC rates not in expected format from fallback API");
                return null;
            }
            Map<String, Object> btcRates = jsonMapper.convertValue(btcRatesObj, new TypeReference<Map<String, Object>>() {});
            String targetCurrency = currency.toLowerCase(Locale.ROOT);
            if (!btcRates.containsKey(targetCurrency)) {
                logger.warn("Currency {} not found in fallback API", currencyCode);
                return null;
            }
            Object rateObj = btcRates.get(targetCurrency);
            if (rateObj == null) {
                logger.warn("Invalid exchange rate for {} from fallback API", currencyCode);
                return null;
            }
            BigDecimal btcToTargetCurrency = parseRate(rateObj, currencyCode);
            if (btcToTargetCurrency == null) {
                return null;
            }
            logger.info("Successfully fetched BTC price in {} from fallback API: {}", currencyCode, btcToTargetCurrency);
            return btcToTargetCurrency;
        } catch (Exception e) {
            logger.error("Failed to fetch BTC price in {} from fallback API", currencyCode, e);
            return null;
        }
    }

    private BigDecimal parseRate(Object rateObj, String currencyCode) {
        if (rateObj instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (rateObj instanceof String string) {
            return parseStringRate(string, currencyCode);
        }
        logger.warn("Unexpected exchange rate format for {} from fallback API: {}", currencyCode, rateObj.getClass().getSimpleName());
        return null;
    }

    private BigDecimal parseStringRate(String rate, String currencyCode) {
        try {
            return new BigDecimal(rate);
        } catch (NumberFormatException _) {
            logger.warn("Cannot parse exchange rate for {} from fallback API: {}", currencyCode, rate);
            return null;
        }
    }

    private BigDecimal getFromCoinGecko(String currency) {
        String currencyCode = currency.toUpperCase(Locale.ROOT);
        String targetCurrency = currency.toLowerCase(Locale.ROOT);
        String apiUrl = String.format(aratiriProperties.getCoingeckoApiUrlTemplate(), currency.toLowerCase(Locale.ROOT));
        try {
            String jsonResponse = restTemplate.getForObject(apiUrl, String.class);
            Map<String, Map<String, Double>> response = jsonMapper.readValue(jsonResponse, new TypeReference<>() {
            });
            if (response != null && response.containsKey(BITCOIN_ID) && response.get(BITCOIN_ID).containsKey(targetCurrency)) {
                Double price = response.get(BITCOIN_ID).get(targetCurrency);
                logger.info("Successfully fetched BTC price from CoinGecko. {}: {}", currencyCode, price);
                return BigDecimal.valueOf(price);
            } else {
                logger.warn("Invalid response format or currency not found from Currency Conversion API for: {}", currency);
                return null;
            }
        } catch (Exception e) {
            logger.error("Failed to fetch BTC price in {} from Currency Conversion API. Returning null.", currencyCode, e);
            return null;
        }
    }
}
