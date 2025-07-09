package com.aratiri.aratiri.config;


import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Data
public class AratiriProperties {

    private final Logger logger = LoggerFactory.getLogger(AratiriProperties.class);

    @Value("${lnd.path.macaroon.admin}")
    private String adminMacaroonPath;

    @Value("${lnd.path.tls.cert}")
    private String lndTlsCertPath;

    @Value("${grpc.client.lnd.name}")
    private String grpcClientLndName;
    @Value("${grpc.client.lnd.port}")
    private int grpcClientLndPort;

    @Value("${aratiri.base.url}")
    private String aratiriBaseUrl;
}