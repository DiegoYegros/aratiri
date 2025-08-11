package com.aratiri.aratiri.config;

import com.aratiri.aratiri.interceptor.GrpcLoggingInterceptor;
import invoicesrpc.InvoicesGrpc;
import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import lnrpc.LightningGrpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import routerrpc.RouterGrpc;

import javax.net.ssl.SSLException;
import java.io.File;
import java.util.concurrent.TimeUnit;

@Configuration
public class GrpcClientConfig {

    private final AratiriProperties properties;
    private final Logger logger = LoggerFactory.getLogger(GrpcClientConfig.class);

    public GrpcClientConfig(AratiriProperties properties) {
        this.properties = properties;
    }

    @Bean
    public LightningGrpc.LightningBlockingStub lightningBlockingStub(ManagedChannel channel) {
        return LightningGrpc.newBlockingStub(channel)
                .withCallCredentials(new MacaroonCallCredentials(properties.getAdminMacaroonPath()));
    }

    @Bean
    public LightningGrpc.LightningStub lightningAsyncStub(ManagedChannel channel) {
        return LightningGrpc.newStub(channel)
                .withCallCredentials(new MacaroonCallCredentials(properties.getAdminMacaroonPath()));
    }

    @Bean
    public RouterGrpc.RouterBlockingStub routerBlockingStub(ManagedChannel channel) {
        return RouterGrpc.newBlockingStub(channel)
                .withCallCredentials(new MacaroonCallCredentials(properties.getAdminMacaroonPath()));
    }

    @Bean
    public RouterGrpc.RouterFutureStub routerFutureStub(ManagedChannel channel) {
        return RouterGrpc.newFutureStub(channel)
                .withCallCredentials(new MacaroonCallCredentials(properties.getAdminMacaroonPath()));
    }

    @Bean
    public InvoicesGrpc.InvoicesBlockingStub invoicesBlockingStub(ManagedChannel channel) {
        return InvoicesGrpc.newBlockingStub(channel)
                .withCallCredentials(new MacaroonCallCredentials(properties.getAdminMacaroonPath()));
    }

    @Bean
    public InvoicesGrpc.InvoicesStub invoicesAsyncStub(ManagedChannel channel) {
        return InvoicesGrpc.newStub(channel)
                .withCallCredentials(new MacaroonCallCredentials(properties.getAdminMacaroonPath()));
    }

    @Bean
    public ManagedChannel lndChannel() throws SSLException {
        String host = properties.getGrpcClientLndName();
        int port = properties.getGrpcClientLndPort();

        logger.info("Connecting to LND at {}:{}", host, port);

        NettyChannelBuilder builder = NettyChannelBuilder.forAddress(host, port)
                .maxInboundMessageSize(64 * 1024 * 1024)
                .keepAliveTime(30, TimeUnit.SECONDS)
                .keepAliveTimeout(5, TimeUnit.SECONDS)
                .keepAliveWithoutCalls(true);

        if (!properties.getLndTlsCertPath().isEmpty() && properties.isGrpcTlsActive()) {
            File tlsCert = new File(properties.getLndTlsCertPath());
            logger.info("Using TLS certificate: {}", tlsCert.getAbsolutePath());

            if (!tlsCert.exists()) {
                throw new RuntimeException("TLS certificate not found: " + tlsCert.getAbsolutePath());
            }

            builder.sslContext(
                    GrpcSslContexts.forClient()
                            .trustManager(tlsCert)
                            .build()
            );
        } else {
            logger.info("Using default TLS");
            builder.useTransportSecurity();
        }
        builder.intercept(new GrpcLoggingInterceptor());
        return builder.build();
    }
}