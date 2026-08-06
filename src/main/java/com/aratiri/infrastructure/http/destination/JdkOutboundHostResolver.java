package com.aratiri.infrastructure.http.destination;

import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

@Component
public class JdkOutboundHostResolver implements OutboundHostResolver {

  @Override
  public List<InetAddress> resolveAll(String host) throws UnknownHostException {
    return Arrays.asList(InetAddress.getAllByName(host));
  }
}
