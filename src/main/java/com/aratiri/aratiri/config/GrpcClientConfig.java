package com.aratiri.aratiri.config;

import com.aratiri.aratiri.interceptor.LoggingInterceptor;
import io.grpc.Channel;
import io.grpc.ClientInterceptors;
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
        Channel withInterceptors = ClientInterceptors.intercept(channel, new LoggingInterceptor());
        return LightningGrpc.newBlockingStub(withInterceptors)
                .withCallCredentials(new MacaroonCallCredentials(properties.getAdminMacaroon()));
    }

    @Bean
    public ManagedChannel lndChannel() throws SSLException {
        File cert = new File(properties.getLndTlsCertPath());
        return NettyChannelBuilder.forAddress("localhost", 10009)
                .sslContext(GrpcSslContexts.forClient().trustManager(cert).build())
                .build();
    }
}