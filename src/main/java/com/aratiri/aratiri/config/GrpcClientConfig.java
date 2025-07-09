package com.aratiri.aratiri.config;

import com.aratiri.aratiri.service.impl.InvoiceServiceImpl;
import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import lnrpc.LightningGrpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.SSLException;
import java.io.File;

@Configuration
public class GrpcClientConfig {

    private final AratiriProperties properties;
    private final Logger logger = LoggerFactory.getLogger(GrpcClientConfig.class);
    public GrpcClientConfig(AratiriProperties properties) {
        this.properties = properties;
    }

    @Bean
    public LightningGrpc.LightningBlockingStub lightningBlockingStub(ManagedChannel channel) {
        logger.info("aratiri props is: {}", properties);
        return LightningGrpc.newBlockingStub(channel)
                .withCallCredentials(new MacaroonCallCredentials(properties.getAdminMacaroonPath()));
    }

    @Bean
    public LightningGrpc.LightningStub lightningAsyncStub(ManagedChannel channel) {
        logger.info("aratiri props is: {}", properties);
        return LightningGrpc.newStub(channel)
                .withCallCredentials(new MacaroonCallCredentials(properties.getAdminMacaroonPath()));
    }

    @Bean
    public ManagedChannel lndChannel() throws SSLException {
        File cert = new File(properties.getLndTlsCertPath());
        return NettyChannelBuilder.forAddress(properties.getGrpcClientLndName(), properties.getGrpcClientLndPort())
                .sslContext(GrpcSslContexts.forClient().trustManager(cert).build())
                .build();
    }
}