package com.aratiri.decoder.infrastructure.nostr;

import com.aratiri.decoder.application.port.out.NostrPort;
import com.aratiri.infrastructure.configuration.WebConfig;
import com.aratiri.infrastructure.http.destination.OutboundDestinationPolicy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.json.JsonMapper;

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
            @Qualifier(WebConfig.OUTBOUND_USER_HTTP) RestTemplate restTemplate,
            JsonMapper jsonMapper,
            OutboundDestinationPolicy outboundDestinationPolicy,
            @Value("${nostr.active:false}") boolean active) {
        if (active) {
            return new NostrAdapter(restTemplate, nostrClient, jsonMapper, outboundDestinationPolicy);
        } else {
            return new NoopNostrAdapter();
        }
    }

}
