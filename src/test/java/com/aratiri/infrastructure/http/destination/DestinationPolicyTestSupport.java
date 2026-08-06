package com.aratiri.infrastructure.http.destination;


import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

final class DestinationPolicyTestSupport {

  private DestinationPolicyTestSupport() {
  }

  static OutboundDestinationProperties defaultProperties() {
    return new OutboundDestinationProperties();
  }

  static OutboundDestinationProperties labProperties() {
    OutboundDestinationProperties properties = new OutboundDestinationProperties();
    properties.setAllowHttp(true);
    properties.setAllowPrivateNetworks(true);
    return properties;
  }

  static OutboundDestinationPolicy policy(
      OutboundDestinationProperties properties,
      OutboundHostResolver resolver) {
    return new OutboundDestinationPolicy(properties, resolver);
  }

  static OutboundDestinationPolicy policyWithResolver(OutboundHostResolver resolver) {
    return policy(defaultProperties(), resolver);
  }

  static InetAddress ipv4(String literal) {
    try {
      return InetAddress.getByName(literal);
    } catch (UnknownHostException e) {
      throw new IllegalStateException(e);
    }
  }

  static InetAddress ipv6(String literal) {
    try {
      return InetAddress.getByName(literal);
    } catch (UnknownHostException e) {
      throw new IllegalStateException(e);
    }
  }

  static final class FakeResolver implements OutboundHostResolver {
    private final Map<String, List<InetAddress>> answers = new ConcurrentHashMap<>();
    private final Map<String, UnknownHostException> failures = new ConcurrentHashMap<>();
    private final AtomicInteger resolveCount = new AtomicInteger();

    FakeResolver put(String host, InetAddress... addresses) {
      answers.put(host.toLowerCase(), List.of(addresses));
      return this;
    }

    FakeResolver fail(String host) {
      failures.put(host.toLowerCase(), new UnknownHostException(host));
      return this;
    }

    int resolveCount() {
      return resolveCount.get();
    }

    @Override
    public List<InetAddress> resolveAll(String host) throws UnknownHostException {
      resolveCount.incrementAndGet();
      String key = host.toLowerCase();
      if (failures.containsKey(key)) {
        throw failures.get(key);
      }
      List<InetAddress> result = answers.get(key);
      if (result == null) {
        throw new UnknownHostException(host);
      }
      return new ArrayList<>(result);
    }
  }
}
