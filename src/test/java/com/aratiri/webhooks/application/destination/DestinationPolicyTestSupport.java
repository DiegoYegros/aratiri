package com.aratiri.webhooks.application.destination;

import com.aratiri.infrastructure.configuration.WebhookDestinationProperties;

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

  static WebhookDestinationProperties defaultProperties() {
    return new WebhookDestinationProperties();
  }

  static WebhookDestinationProperties labProperties() {
    WebhookDestinationProperties properties = new WebhookDestinationProperties();
    properties.setAllowHttp(true);
    properties.setAllowPrivateNetworks(true);
    return properties;
  }

  static WebhookDestinationPolicy policy(
      WebhookDestinationProperties properties,
      WebhookHostResolver resolver) {
    return new WebhookDestinationPolicy(properties, resolver);
  }

  static WebhookDestinationPolicy policyWithResolver(WebhookHostResolver resolver) {
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

  static final class FakeResolver implements WebhookHostResolver {
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
