package com.aratiri.infrastructure.http.destination;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JdkOutboundHostResolverTest {

  @Test
  void resolveAll_returnsAddressesForLoopbackLiteral() throws Exception {
    JdkOutboundHostResolver resolver = new JdkOutboundHostResolver();
    List<InetAddress> addresses = resolver.resolveAll("127.0.0.1");
    assertNotNull(addresses);
    assertFalse(addresses.isEmpty());
  }
}
