package com.aratiri.infrastructure.configuration;


import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
@Data
public class AratiriProperties {

    /**
     * Minimum {@code jwt.secret} / {@code JWT_SECRET} length in UTF-8 bytes.
     * HS256 signing keys require at least 256 bits; {@link #jwtSecret} is encoded with UTF-8
     * before use (see {@code JwtUtil} / local {@code JwtDecoder}).
     */
    public static final int MIN_JWT_SECRET_UTF8_BYTES = 32;

    @Value("${lnd.path.macaroon.admin}")
    private String adminMacaroonPath;

    @Value("${grpc.client.lnd.name}")
    private String grpcClientLndName;

    @Value("${grpc.client.lnd.port}")
    private int grpcClientLndPort;

    @Value("${aratiri.base.url}")
    private String aratiriBaseUrl;

    /**
     * Public frontend origin used for owner-facing payment-request share URLs
     * ({@code /pay/{publicId}}). Independent of {@link #aratiriBaseUrl}, which remains
     * the API/LNURL callback base.
     */
    @Value("${aratiri.frontend.base.url:http://localhost:3000}")
    private String frontendBaseUrl;

    @Value("${lnd.path.tls.cert}")
    private String lndTlsCertPath;

    @Value("${grpc.tls.active:true}")
    private boolean grpcTlsActive;

    @Value("${aratiri.accounts.fiat.currencies:usd,ars,eur,pyg}")
    private List<String> fiatCurrencies;

    @Value("${aratiri.currency.conversion.api.url:https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=%s}")
    private String coingeckoApiUrlTemplate;

    @Value("${aratiri.currency.conversion.history.api.url:https://api.coingecko.com/api/v3/coins/bitcoin/market_chart?vs_currency=%s&days=%s}")
    private String coingeckoMarketChartApiUrlTemplate;

    @Value("${aratiri.currency.conversion.fallback.api.url:https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/%s.json}")
    private String fallbackApiUrlTemplate;

    @Value("${aratiri.currency.conversion.cache.current.ttl-seconds:10}")
    private long btcPriceCurrentCacheTtlSeconds;

    @Value("${aratiri.currency.conversion.cache.history.ttl-seconds:300}")
    private long btcPriceHistoryCacheTtlSeconds;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    @Value("${jwt.refresh-expiration}")
    private long jwtRefreshExpiration;

    /**
     * Fail closed at startup if {@code jwt.secret} is blank or shorter than
     * {@link #MIN_JWT_SECRET_UTF8_BYTES} UTF-8 bytes.
     */
    @PostConstruct
    void validateJwtSecret() {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException(
                    "jwt.secret (JWT_SECRET) must be set and non-blank; minimum length is "
                            + MIN_JWT_SECRET_UTF8_BYTES + " UTF-8 bytes");
        }
        int utf8Bytes = jwtSecret.getBytes(StandardCharsets.UTF_8).length;
        if (utf8Bytes < MIN_JWT_SECRET_UTF8_BYTES) {
            throw new IllegalStateException(
                    "jwt.secret (JWT_SECRET) must be at least " + MIN_JWT_SECRET_UTF8_BYTES
                            + " UTF-8 bytes (got " + utf8Bytes + ")");
        }
    }
}
