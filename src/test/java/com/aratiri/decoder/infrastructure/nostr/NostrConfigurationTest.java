package com.aratiri.decoder.infrastructure.nostr;

import com.aratiri.decoder.application.port.out.NostrPort;
import com.aratiri.infrastructure.http.destination.OutboundDestinationPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;

class NostrConfigurationTest {

  @Test
  void nostrClient_activeUsesRealClient() {
    NostrConfiguration configuration = new NostrConfiguration();
    assertInstanceOf(NostrClientImpl.class, configuration.nostrClient(true));
    assertInstanceOf(NoopNostrClient.class, configuration.nostrClient(false));
  }

  @Test
  void nostrPort_activeUsesNostrAdapter() {
    NostrConfiguration configuration = new NostrConfiguration();
    NostrPort active = configuration.nostrPort(
        mock(NostrClient.class),
        mock(RestTemplate.class),
        new JsonMapper(),
        mock(OutboundDestinationPolicy.class),
        true);
    NostrPort inactive = configuration.nostrPort(
        mock(NostrClient.class),
        mock(RestTemplate.class),
        new JsonMapper(),
        mock(OutboundDestinationPolicy.class),
        false);

    assertInstanceOf(NostrAdapter.class, active);
    assertInstanceOf(NoopNostrAdapter.class, inactive);
  }
}
