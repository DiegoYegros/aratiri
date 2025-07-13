package com.aratiri.aratiri.config;

import com.aratiri.aratiri.interceptor.GrpcLoggingInterceptor;
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
        logger.info("Aratiri props: {}", properties);
        return LightningGrpc.newBlockingStub(channel)
                .withCallCredentials(new MacaroonCallCredentials(properties.getAdminMacaroonPath()));
    }

    @Bean
    public LightningGrpc.LightningStub lightningAsyncStub(ManagedChannel channel) {
        logger.info("Aratiri props: {}", properties);
        return LightningGrpc.newStub(channel)
                .withCallCredentials(new MacaroonCallCredentials(properties.getAdminMacaroonPath()));
    }

    @Bean
    public ManagedChannel lndChannel() throws SSLException {
        NettyChannelBuilder nettyChannelBuilder = NettyChannelBuilder.forAddress(properties.getGrpcClientLndName(), properties.getGrpcClientLndPort()).intercept(new GrpcLoggingInterceptor());
        if (!properties.getLndTlsCertPath().isEmpty() && properties.isGrpcTlsActive()){
            logger.info("Both grpc.tls.active and lnd.path.tls.cert are present. Defaulting to SSL_CONTEXT with TLS CERT.");
            File cert = new File(properties.getLndTlsCertPath());
            nettyChannelBuilder.sslContext(GrpcSslContexts.forClient().trustManager(cert).build());
        } else {
            logger.info("Using TLS.");
            nettyChannelBuilder.useTransportSecurity();
        }
        return nettyChannelBuilder
                .build();
    }
}