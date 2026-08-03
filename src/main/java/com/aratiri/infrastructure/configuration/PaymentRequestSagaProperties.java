package com.aratiri.infrastructure.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Setter
@Getter
@ConfigurationProperties(prefix = "aratiri.payment-requests.saga")
public class PaymentRequestSagaProperties {

    private long fixedDelayMs = 1000;
    private int batchSize = 10;
    private int leaseSeconds = 300;
    private int provisionMaxAttempts = 10;
    private int cancelMaxAttempts = 10;
    private long backoffBaseMs = 1000;
    private long backoffMaxMs = 60000;
}
