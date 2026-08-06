package com.aratiri.infrastructure.http.destination;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutboundDestinationPropertiesTest {

  @Test
  void defaultsAreFailClosed() {
    OutboundDestinationProperties properties = new OutboundDestinationProperties();
    assertFalse(properties.isAllowHttp());
    assertFalse(properties.isAllowPrivateNetworks());
    assertTrue(properties.getAllowedHosts().isEmpty());
  }

  @Test
  void escapeHatchesAreExplicitlySettable() {
    OutboundDestinationProperties properties = new OutboundDestinationProperties();
    properties.setAllowHttp(true);
    properties.setAllowPrivateNetworks(true);
    properties.setAllowedHosts(List.of("hooks.example.com", "*.trusted.example"));

    assertTrue(properties.isAllowHttp());
    assertTrue(properties.isAllowPrivateNetworks());
    assertEquals(List.of("hooks.example.com", "*.trusted.example"), properties.getAllowedHosts());
  }
}
