package com.aratiri.aratiri;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class AratiriApplication {

    public static void main(String[] args) {
        SpringApplication.run(AratiriApplication.class, args);
    }

}
