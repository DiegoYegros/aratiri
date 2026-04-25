package com.aratiri.infrastructure.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Setter
@Getter
@ConfigurationProperties(prefix = "aratiri.node-operations")
public class NodeOperationProperties {

    private long fixedDelayMs = 1000;
    private int batchSize = 10;
    private int leaseSeconds = 300;
    private int lightningMaxAttempts = 5;
    private int onchainMaxAttempts = 5;
}
