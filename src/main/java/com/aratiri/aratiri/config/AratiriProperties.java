package com.aratiri.aratiri.config;


import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

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

}