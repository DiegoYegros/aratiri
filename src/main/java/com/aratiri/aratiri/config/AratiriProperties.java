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

    @Value("${aratiri.accounts.fiat.currencies:usd,ars,eth,eur,pyg}")
    private List<String> fiatCurrencies;
}