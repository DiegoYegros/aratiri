package com.aratiri.aratiri.config;

import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import lnrpc.LightningGrpc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.SSLException;
import java.io.File;

@Configuration
public class GrpcClientConfig {

    private final Properties properties;

    public GrpcClientConfig(Properties properties) {
        this.properties = properties;
    }

    @Bean
    public LightningGrpc.LightningBlockingStub lightningBlockingStub(ManagedChannel channel) {
        return LightningGrpc.newBlockingStub(channel)
                .withCallCredentials(new MacaroonCallCredentials(properties.getAdminMacaroon()));
    }

    @Bean
    public ManagedChannel lndChannel() throws SSLException {
        File cert = new File(properties.getLndTlsCertPath());
        return NettyChannelBuilder.forAddress(properties.getGrpcClientLndName(), properties.getGrpcClientLndPort())
                .sslContext(GrpcSslContexts.forClient().trustManager(cert).build())
                .build();
    }
}