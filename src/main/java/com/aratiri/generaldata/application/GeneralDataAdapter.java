package com.aratiri.generaldata.application;

import com.aratiri.generaldata.application.dto.BtcPriceHistoryResponseDTO;
import com.aratiri.generaldata.application.dto.CurrentBtcPriceResponseDTO;
import com.aratiri.generaldata.application.port.in.GeneralDataPort;
import com.aratiri.generaldata.domain.BtcPricePoint;
import com.aratiri.generaldata.domain.BtcPriceRange;
import com.aratiri.generaldata.domain.BtcPriceSnapshot;
import com.aratiri.infrastructure.configuration.AratiriProperties;
import com.aratiri.shared.exception.AratiriException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class GeneralDataAdapter implements GeneralDataPort {

    private final CachedBtcPriceService cachedBtcPriceService;
    private final AratiriProperties aratiriProperties;

    public GeneralDataAdapter(CachedBtcPriceService cachedBtcPriceService, AratiriProperties aratiriProperties) {
        this.cachedBtcPriceService = cachedBtcPriceService;
        this.aratiriProperties = aratiriProperties;
    }

    @Override
    public Map<String, BigDecimal> getCurrentBtcPrice() {
        return cachedBtcPriceService.getCurrentBtcPriceSnapshot().prices();
    }

    @Override
    public CurrentBtcPriceResponseDTO getCurrentBtcPrice(String currency) {
        String normalizedCurrency = normalizeAndValidateCurrency(currency);
        BtcPriceSnapshot snapshot = cachedBtcPriceService.getCurrentBtcPriceSnapshot();
        BigDecimal price = snapshot.prices().get(normalizedCurrency);
        if (price == null) {
            throw new AratiriException(
                    "Current BTC price is unavailable for currency [" + normalizedCurrency + "].",
                    HttpStatus.SERVICE_UNAVAILABLE.value()
            );
        }
        return new CurrentBtcPriceResponseDTO(normalizedCurrency, price, snapshot.updatedAt());
    }

    @Override
    public BtcPriceHistoryResponseDTO getBtcPriceHistory(String currency, String range) {
        String normalizedCurrency = normalizeAndValidateCurrency(currency);
        BtcPriceRange normalizedRange = BtcPriceRange.fromCode(range);
        try {
            List<BtcPricePoint> points = cachedBtcPriceService.getBtcPriceHistory(normalizedCurrency, normalizedRange);
            return new BtcPriceHistoryResponseDTO(normalizedCurrency, normalizedRange.code(), points);
        } catch (IllegalStateException _) {
            throw new AratiriException(
                    "BTC price history is unavailable for currency [" + normalizedCurrency + "] and range [" + normalizedRange.code() + "].",
                    HttpStatus.SERVICE_UNAVAILABLE.value()
            );
        }
    }

    @Override
    public List<String> getFiatCurrencies() {
        return aratiriProperties.getFiatCurrencies();
    }

    private String normalizeAndValidateCurrency(String currency) {
        String normalizedCurrency = currency == null ? "" : currency.trim().toLowerCase(Locale.ROOT);
        if (normalizedCurrency.isBlank()) {
            throw new IllegalArgumentException("Currency is required.");
        }
        List<String> supportedCurrencies = aratiriProperties.getFiatCurrencies().stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .toList();
        if (!supportedCurrencies.contains(normalizedCurrency)) {
            throw new IllegalArgumentException(
                    "Unsupported currency [" + currency + "]. Allowed values: " + String.join(", ", supportedCurrencies)
            );
        }
        return normalizedCurrency;
    }
}
