package com.aratiri.aratiri.config;


import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@Data
public class AratiriProperties {

    @Value("${lnd.path.macaroon.admin}")
    private String adminMacaroonPath;

    @Value("${grpc.client.lnd.name}")
    private String grpcClientLndName;

    @Value("${grpc.client.lnd.port}")
    private int grpcClientLndPort;

    @Value("${aratiri.base.url}")
    private String aratiriBaseUrl;

    @Value("${lnd.path.tls.cert}")
    private String lndTlsCertPath;

    @Value("${grpc.tls.active:true}")
    private boolean grpcTlsActive;

    @Value("${aratiri.accounts.fiat.currencies:usd,ars,eur,pyg}")
    private List<String> fiatCurrencies;

    @Value("${aratiri.currency.conversion.api.url:https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=%s}")
    private String coingeckoApiUrlTemplate;

    @Value("${aratiri.currency.conversion.fallback.api.url:https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/%s.json}")
    private String fallbackApiUrlTemplate;
}