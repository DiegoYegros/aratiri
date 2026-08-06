package com.aratiri.infrastructure.configuration;

import com.aratiri.infrastructure.web.context.AratiriContextArgumentResolver;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class WebConfigOutboundUserHttpTest {

  @Test
  void outboundUserHttpRestTemplate_doesNotFollowRedirectToPrivate() throws Exception {
    AtomicInteger privateHits = new AtomicInteger();
    HttpServer privateServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    privateServer.createContext("/", exchange -> {
      privateHits.incrementAndGet();
      byte[] body = "SECRET".getBytes();
      exchange.sendResponseHeaders(200, body.length);
      exchange.getResponseBody().write(body);
      exchange.close();
    });
    privateServer.start();

    HttpServer publicServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    int privatePort = privateServer.getAddress().getPort();
    publicServer.createContext("/meta", exchange -> {
      exchange.getResponseHeaders().add("Location", "http://127.0.0.1:" + privatePort + "/");
      exchange.sendResponseHeaders(302, -1);
      exchange.close();
    });
    publicServer.start();

    try {
      WebConfig config = new WebConfig(mock(AratiriContextArgumentResolver.class));
      RestTemplate restTemplate = config.outboundUserHttpRestTemplate();
      String url = "http://127.0.0.1:" + publicServer.getAddress().getPort() + "/meta";
      try {
        restTemplate.getForObject(url, String.class);
      } catch (Exception _) {
        // 302 may or may not throw depending on response handling; private must stay cold.
      }
      assertEquals(0, privateHits.get());
    } finally {
      publicServer.stop(0);
      privateServer.stop(0);
    }
  }
}
