package com.aratiri.webhooks.application;

import com.aratiri.infrastructure.http.destination.OutboundDestinationPolicy;
import com.aratiri.infrastructure.http.destination.OutboundDestinationProperties;
import com.aratiri.infrastructure.http.destination.OutboundHostResolver;
import com.aratiri.infrastructure.persistence.jpa.entity.WebhookDeliveryEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.WebhookDeliveryStatus;
import com.aratiri.infrastructure.persistence.jpa.entity.WebhookEndpointEntity;
import com.aratiri.webhooks.application.destination.WebhookDestinationPolicy;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JavaHttpWebhookDeliverySenderTest {

  @Test
  void generateSignature_producesValidHmac() {
    String secret = "test-secret";
    String signature = JavaHttpWebhookDeliverySender.generateSignature(
        secret, "1234567890", "evt-1", "{}");

    assertTrue(signature.startsWith("v1="));
    assertNotEquals("v1=", signature);
  }

  @Test
  void generateSignature_isDeterministic() {
    String secret = "test-secret";
    String sig1 = JavaHttpWebhookDeliverySender.generateSignature(
        secret, "1234567890", "evt-1", "{}");
    String sig2 = JavaHttpWebhookDeliverySender.generateSignature(
        secret, "1234567890", "evt-1", "{}");

    assertEquals(sig1, sig2);
  }

  @Test
  void generateSignature_differsWithDifferentPayloads() {
    String secret = "test-secret";
    String sig1 = JavaHttpWebhookDeliverySender.generateSignature(
        secret, "1234567890", "evt-1", "{}");
    String sig2 = JavaHttpWebhookDeliverySender.generateSignature(
        secret, "1234567890", "evt-1", "{\"x\":1}");

    assertNotEquals(sig1, sig2);
  }

  @Test
  void generateSignature_differsWithDifferentTimestamps() {
    String secret = "test-secret";
    String sig1 = JavaHttpWebhookDeliverySender.generateSignature(
        secret, "100", "evt-1", "{}");
    String sig2 = JavaHttpWebhookDeliverySender.generateSignature(
        secret, "200", "evt-1", "{}");

    assertNotEquals(sig1, sig2);
  }

  @Test
  void generateSignature_differsWithDifferentEventIds() {
    String secret = "test-secret";
    String sig1 = JavaHttpWebhookDeliverySender.generateSignature(
        secret, "100", "evt-1", "{}");
    String sig2 = JavaHttpWebhookDeliverySender.generateSignature(
        secret, "100", "evt-2", "{}");

    assertNotEquals(sig1, sig2);
  }

  @Test
  void generateSignature_differsWithDifferentSecrets() {
    String sig1 = JavaHttpWebhookDeliverySender.generateSignature(
        "secret-a", "100", "evt-1", "{}");
    String sig2 = JavaHttpWebhookDeliverySender.generateSignature(
        "secret-b", "100", "evt-1", "{}");

    assertNotEquals(sig1, sig2);
  }

  @Test
  void generateSignature_handlesEmptyPayload() {
    String signature = JavaHttpWebhookDeliverySender.generateSignature(
        "secret", "100", "evt-1", "");

    assertTrue(signature.startsWith("v1="));
    assertTrue(signature.length() > 5);
  }

  @Test
  void generateSignature_handlesSpecialCharacters() {
    String signature = JavaHttpWebhookDeliverySender.generateSignature(
        "secret+with/special=chars", "100", "evt-1", "{\"msg\":\"hello world\"}");

    assertTrue(signature.startsWith("v1="));
    assertTrue(signature.length() > 5);
  }

  @Test
  void constructor_createsHttpClient() {
    JavaHttpWebhookDeliverySender sender = new JavaHttpWebhookDeliverySender(labPolicy());
    assertNotNull(sender);
  }

  @Test
  void packagePrivateConstructor_acceptsCustomHttpClient() {
    HttpClient client = HttpClient.newHttpClient();
    JavaHttpWebhookDeliverySender sender = new JavaHttpWebhookDeliverySender(client, labPolicy());
    assertNotNull(sender);
  }

  @Test
  void send_deliversPayloadAndReturnsSuccessResponse() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext("/", exchange -> {
      assertEquals("POST", exchange.getRequestMethod());
      String body = new String(exchange.getRequestBody().readAllBytes());
      assertEquals("{\"test\":true}", body);

      byte[] response = "{\"ok\":true}".getBytes();
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, response.length);
      exchange.getResponseBody().write(response);
      exchange.close();
    });
    server.start();
    try {
      int port = server.getAddress().getPort();
      WebhookEndpointEntity endpoint = endpoint("http://localhost:" + port + "/");
      WebhookDeliveryEntity delivery = delivery("{\"test\":true}");

      JavaHttpWebhookDeliverySender sender = new JavaHttpWebhookDeliverySender(labPolicy());
      WebhookSendResult result = sender.send(delivery, endpoint);

      assertEquals(200, result.statusCode());
      assertTrue(result.body().contains("ok"));
      assertTrue(result.successful());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void send_returnsSuccessFor201Created() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext("/", exchange -> {
      exchange.sendResponseHeaders(201, -1);
      exchange.close();
    });
    server.start();
    try {
      int port = server.getAddress().getPort();
      WebhookEndpointEntity endpoint = endpoint("http://localhost:" + port + "/");
      WebhookDeliveryEntity delivery = delivery("{}");

      JavaHttpWebhookDeliverySender sender = new JavaHttpWebhookDeliverySender(labPolicy());
      WebhookSendResult result = sender.send(delivery, endpoint);

      assertEquals(201, result.statusCode());
      assertTrue(result.successful());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void send_returnsSuccessFor204NoContent() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext("/", exchange -> {
      exchange.sendResponseHeaders(204, -1);
      exchange.close();
    });
    server.start();
    try {
      int port = server.getAddress().getPort();
      WebhookEndpointEntity endpoint = endpoint("http://localhost:" + port + "/");
      WebhookDeliveryEntity delivery = delivery("{}");

      JavaHttpWebhookDeliverySender sender = new JavaHttpWebhookDeliverySender(labPolicy());
      WebhookSendResult result = sender.send(delivery, endpoint);

      assertEquals(204, result.statusCode());
      assertTrue(result.successful());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void send_returnsErrorResponseFor4xx() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext("/", exchange -> {
      byte[] response = "Not Found".getBytes();
      exchange.sendResponseHeaders(404, response.length);
      exchange.getResponseBody().write(response);
      exchange.close();
    });
    server.start();
    try {
      int port = server.getAddress().getPort();
      WebhookEndpointEntity endpoint = endpoint("http://localhost:" + port + "/");
      WebhookDeliveryEntity delivery = delivery("{}");

      JavaHttpWebhookDeliverySender sender = new JavaHttpWebhookDeliverySender(labPolicy());
      WebhookSendResult result = sender.send(delivery, endpoint);

      assertEquals(404, result.statusCode());
      assertEquals("Not Found", result.body());
      assertFalse(result.successful());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void send_returnsErrorResponseFor5xx() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext("/", exchange -> {
      byte[] response = "Internal Error".getBytes();
      exchange.sendResponseHeaders(500, response.length);
      exchange.getResponseBody().write(response);
      exchange.close();
    });
    server.start();
    try {
      int port = server.getAddress().getPort();
      WebhookEndpointEntity endpoint = endpoint("http://localhost:" + port + "/");
      WebhookDeliveryEntity delivery = delivery("{}");

      JavaHttpWebhookDeliverySender sender = new JavaHttpWebhookDeliverySender(labPolicy());
      WebhookSendResult result = sender.send(delivery, endpoint);

      assertEquals(500, result.statusCode());
      assertEquals("Internal Error", result.body());
      assertFalse(result.successful());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void send_setsCorrectRequestHeaders() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext("/", exchange -> {
      assertEquals("application/json", exchange.getRequestHeaders().getFirst("Content-Type"));
      assertEquals("Aratiri-Webhooks/1.0", exchange.getRequestHeaders().getFirst("User-Agent"));
      assertNotNull(exchange.getRequestHeaders().getFirst("X-Aratiri-Event-Id"));
      assertEquals("payment.succeeded", exchange.getRequestHeaders().getFirst("X-Aratiri-Event-Type"));
      assertNotNull(exchange.getRequestHeaders().getFirst("X-Aratiri-Delivery-Id"));
      assertNotNull(exchange.getRequestHeaders().getFirst("X-Aratiri-Timestamp"));
      String sig = exchange.getRequestHeaders().getFirst("X-Aratiri-Signature");
      assertNotNull(sig);
      assertTrue(sig.startsWith("v1="));

      exchange.sendResponseHeaders(204, -1);
      exchange.close();
    });
    server.start();
    try {
      int port = server.getAddress().getPort();
      WebhookEndpointEntity endpoint = endpoint("http://localhost:" + port + "/");
      WebhookDeliveryEntity delivery = delivery("{\"type\":\"test\"}");

      JavaHttpWebhookDeliverySender sender = new JavaHttpWebhookDeliverySender(labPolicy());
      WebhookSendResult result = sender.send(delivery, endpoint);

      assertEquals(204, result.statusCode());
      assertTrue(result.successful());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void send_handlesNullPayload() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext("/", exchange -> {
      String body = new String(exchange.getRequestBody().readAllBytes());
      assertEquals("", body);

      exchange.sendResponseHeaders(200, -1);
      exchange.close();
    });
    server.start();
    try {
      int port = server.getAddress().getPort();
      WebhookEndpointEntity endpoint = endpoint("http://localhost:" + port + "/");
      WebhookDeliveryEntity delivery = WebhookDeliveryEntity.builder()
          .id(UUID.randomUUID())
          .eventId(UUID.randomUUID())
          .endpointId(endpoint.getId())
          .eventType("payment.succeeded")
          .payload(null)
          .status(WebhookDeliveryStatus.PENDING)
          .attemptCount(0)
          .nextAttemptAt(Instant.now())
          .build();

      JavaHttpWebhookDeliverySender sender = new JavaHttpWebhookDeliverySender(labPolicy());
      WebhookSendResult result = sender.send(delivery, endpoint);

      assertEquals(200, result.statusCode());
      assertTrue(result.successful());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void send_throwsIOExceptionWhenServerUnreachable() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.start();
    int port = server.getAddress().getPort();
    server.stop(0);

    WebhookEndpointEntity endpoint = endpoint("http://localhost:" + port + "/");
    WebhookDeliveryEntity delivery = delivery("{}");

    JavaHttpWebhookDeliverySender sender = new JavaHttpWebhookDeliverySender(labPolicy());
    assertThrows(IOException.class, () -> sender.send(delivery, endpoint));
  }

  @Test
  void send_doesNotFollowRedirectToPrivate() throws Exception {
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
    publicServer.createContext("/hook", exchange -> {
      exchange.getResponseHeaders().add("Location", "http://127.0.0.1:" + privatePort + "/");
      exchange.sendResponseHeaders(302, -1);
      exchange.close();
    });
    publicServer.start();

    try {
      int publicPort = publicServer.getAddress().getPort();
      JavaHttpWebhookDeliverySender sender = new JavaHttpWebhookDeliverySender(labPolicy());
      WebhookSendResult result = sender.send(
          delivery("{}"),
          endpoint("http://127.0.0.1:" + publicPort + "/hook"));

      assertEquals(302, result.statusCode());
      assertFalse(result.successful());
      assertEquals(0, privateHits.get());
    } finally {
      publicServer.stop(0);
      privateServer.stop(0);
    }
  }

  @Test
  void send_rejectsInvalidDestinationWithoutCallingHttpClient() throws Exception {
    HttpClient httpClient = mock(HttpClient.class);
    OutboundDestinationProperties properties = new OutboundDestinationProperties();
    OutboundHostResolver resolver = host -> List.of(InetAddress.getByName("10.0.0.1"));
    WebhookDestinationPolicy policy = webhookPolicy(properties, resolver);
    JavaHttpWebhookDeliverySender sender = new JavaHttpWebhookDeliverySender(httpClient, policy);

    WebhookEndpointEntity endpoint = endpoint("https://internal.example.com/webhook?token=secret");
    WebhookDeliveryEntity delivery = delivery("{\"amount\":100}");

    IOException ex = assertThrows(IOException.class, () -> sender.send(delivery, endpoint));
    assertEquals("Webhook destination URL is not allowed", ex.getMessage());
    verify(httpClient, never()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
  }

  @Test
  void send_revalidatesChangedResolutionAndSkipsSend() throws Exception {
    AtomicInteger resolves = new AtomicInteger();
    HttpClient httpClient = mock(HttpClient.class);
    OutboundDestinationProperties properties = new OutboundDestinationProperties();
    OutboundHostResolver resolver = host -> {
      resolves.incrementAndGet();
      return List.of(InetAddress.getByName("127.0.0.1"));
    };
    WebhookDestinationPolicy policy = webhookPolicy(properties, resolver);
    JavaHttpWebhookDeliverySender sender = new JavaHttpWebhookDeliverySender(httpClient, policy);

    IOException ex = assertThrows(IOException.class,
        () -> sender.send(delivery("{}"), endpoint("https://rebinding.example.com/hook")));
    assertEquals("Webhook destination URL is not allowed", ex.getMessage());
    assertEquals(1, resolves.get());
    verify(httpClient, never()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
  }

  @Test
  void send_validPublicDestinationStillSignsAndSends() throws Exception {
    HttpClient httpClient = mock(HttpClient.class);
    @SuppressWarnings("unchecked")
    HttpResponse<String> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(204);
    when(response.body()).thenReturn("");
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

    OutboundDestinationProperties properties = new OutboundDestinationProperties();
    OutboundHostResolver resolver = host -> List.of(InetAddress.getByName("93.184.216.34"));
    WebhookDestinationPolicy policy = webhookPolicy(properties, resolver);
    JavaHttpWebhookDeliverySender sender = new JavaHttpWebhookDeliverySender(httpClient, policy);

    WebhookSendResult result = sender.send(
        delivery("{\"ok\":true}"),
        endpoint("https://hooks.example.com/webhook"));

    assertEquals(204, result.statusCode());
    assertTrue(result.successful());
    verify(httpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
  }

  private static WebhookDestinationPolicy labPolicy() {
    OutboundDestinationProperties properties = new OutboundDestinationProperties();
    properties.setAllowHttp(true);
    properties.setAllowPrivateNetworks(true);
    OutboundHostResolver resolver = host -> List.of(InetAddress.getByName(host));
    return webhookPolicy(properties, resolver);
  }

  private static WebhookDestinationPolicy webhookPolicy(
      OutboundDestinationProperties properties,
      OutboundHostResolver resolver) {
    return new WebhookDestinationPolicy(new OutboundDestinationPolicy(properties, resolver));
  }

  private WebhookEndpointEntity endpoint(String url) {
    return WebhookEndpointEntity.builder()
        .id(UUID.randomUUID())
        .name("Test Endpoint")
        .url(url)
        .signingSecret("test-signing-secret")
        .enabled(true)
        .build();
  }

  private WebhookDeliveryEntity delivery(String payload) {
    return WebhookDeliveryEntity.builder()
        .id(UUID.randomUUID())
        .eventId(UUID.randomUUID())
        .endpointId(UUID.randomUUID())
        .eventType("payment.succeeded")
        .payload(payload)
        .status(WebhookDeliveryStatus.PENDING)
        .attemptCount(0)
        .nextAttemptAt(Instant.now())
        .build();
  }
}
