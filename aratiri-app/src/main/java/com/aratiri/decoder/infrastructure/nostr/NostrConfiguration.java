package com.aratiri.decoder.infrastructure.nostr;

import com.aratiri.decoder.application.port.out.NostrPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class NostrConfiguration {

    @Bean
    public NostrClient nostrClient(@Value("${nostr.active:false}") boolean active) {
        if (!active) {
            return new NoopNostrClient();
        }
        return new NostrClientImpl();
    }

    @Bean
    public NostrPort nostrPort(
            NostrClient nostrClient,
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            @Value("${nostr.active:false}") boolean active) {
        if (active) {
            return new NostrAdapter(restTemplate, nostrClient, objectMapper);
        } else {
            return new NoopNostrAdapter();
        }
    }

}