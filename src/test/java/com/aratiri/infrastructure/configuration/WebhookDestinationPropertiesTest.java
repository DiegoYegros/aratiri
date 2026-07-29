package com.aratiri.infrastructure.configuration;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookDestinationPropertiesTest {

  @Test
  void defaultsAreFailClosed() {
    WebhookDestinationProperties properties = new WebhookDestinationProperties();
    assertFalse(properties.isAllowHttp());
    assertFalse(properties.isAllowPrivateNetworks());
    assertTrue(properties.getAllowedHosts().isEmpty());
  }

  @Test
  void escapeHatchesAreExplicitlySettable() {
    WebhookDestinationProperties properties = new WebhookDestinationProperties();
    properties.setAllowHttp(true);
    properties.setAllowPrivateNetworks(true);
    properties.setAllowedHosts(List.of("hooks.example.com", "*.trusted.example"));

    assertTrue(properties.isAllowHttp());
    assertTrue(properties.isAllowPrivateNetworks());
    assertEquals(List.of("hooks.example.com", "*.trusted.example"), properties.getAllowedHosts());
  }
}
