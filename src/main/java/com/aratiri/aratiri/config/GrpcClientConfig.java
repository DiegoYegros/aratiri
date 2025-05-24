package com.aratiri.aratiri.config;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lnrpc.LightningGrpc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GrpcClientConfig {

    @Bean
    public LightningGrpc.LightningBlockingStub lightningBlockingStub(ManagedChannel channel) {
        return LightningGrpc.newBlockingStub(channel);
    }

    @Bean
    public ManagedChannel lndChannel() {
        return ManagedChannelBuilder.forAddress("localhost", 10009)
                .usePlaintext()
                .build();
    }
}