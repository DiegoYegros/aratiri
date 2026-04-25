package com.aratiri;

import com.aratiri.infrastructure.configuration.NodeOperationProperties;
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
@EnableConfigurationProperties(NodeOperationProperties.class)
public class AratiriApplication {

    public static void main(String[] args) {
        SpringApplication.run(AratiriApplication.class, args);
    }

}