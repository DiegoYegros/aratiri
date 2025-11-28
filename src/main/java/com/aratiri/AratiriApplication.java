package com.aratiri;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.annotation.EnableKafkaRetryTopic;
import org.springframework.resilience.annotation.EnableResilientMethods;

@SpringBootApplication
@EnableCaching
@EnableKafkaRetryTopic
@EnableResilientMethods
@EnableKafka
public class AratiriApplication {

    public static void main(String[] args) {
        SpringApplication.run(AratiriApplication.class, args);
    }

}