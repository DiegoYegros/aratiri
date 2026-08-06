package com.aratiri;

import com.aratiri.infrastructure.configuration.NodeOperationProperties;
import com.aratiri.infrastructure.configuration.OutboxProperties;
import com.aratiri.infrastructure.configuration.PaymentRequestSagaProperties;
import com.aratiri.infrastructure.http.destination.OutboundDestinationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.annotation.EnableKafkaRetryTopic;
import org.springframework.resilience.annotation.EnableResilientMethods;

@SpringBootApplication
@EnableCaching
@EnableKafkaRetryTopic
@EnableResilientMethods
@EnableKafka
@EnableConfigurationProperties({
        NodeOperationProperties.class,
        OutboxProperties.class,
        PaymentRequestSagaProperties.class,
        OutboundDestinationProperties.class
})
public class AratiriApplication {

    public static void main(String[] args) {
        SpringApplication.run(AratiriApplication.class, args);
    }

}