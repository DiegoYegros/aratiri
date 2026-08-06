package com.aratiri.infrastructure.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Setter
@Getter
@ConfigurationProperties(prefix = "aratiri.outbox")
public class OutboxProperties {

    private int batchSize = 200;
    private long fixedDelayMs = 1000;
    private int leaseSeconds = 30;
}
