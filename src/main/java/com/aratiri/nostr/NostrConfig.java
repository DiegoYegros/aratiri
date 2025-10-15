package com.aratiri.nostr;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class NostrConfig {

    @Bean
    public NostrClient nostrClient(@Value("${nostr.active:false}") boolean active) {
        if (!active) {
            return new NoopNostrClient();
        }
        return new NostrClientImpl();
    }

    @Bean
    public NostrService nostrService(
            NostrClient nostrClient,
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            @Value("${nostr.active:false}") boolean active) {
        if (active) {
            return new NostrServiceImpl(restTemplate, nostrClient, objectMapper);
        } else {
            return new NoopNostrService();
        }
    }

}