package com.aratiri.webhooks.application.destination;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/**
 * Resolves a hostname to all A/AAAA addresses. Injectable so policy tests stay offline.
 */
@FunctionalInterface
public interface WebhookHostResolver {

  List<InetAddress> resolveAll(String host) throws UnknownHostException;
}
