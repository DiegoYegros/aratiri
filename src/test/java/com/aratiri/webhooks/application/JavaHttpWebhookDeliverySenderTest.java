package com.aratiri.webhooks.application;

import com.aratiri.infrastructure.persistence.jpa.entity.WebhookDeliveryEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.WebhookDeliveryStatus;
import com.aratiri.infrastructure.persistence.jpa.entity.WebhookEndpointEntity;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

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
    JavaHttpWebhookDeliverySender sender = new JavaHttpWebhookDeliverySender();
    assertNotNull(sender);
  }

  @Test
  void packagePrivateConstructor_acceptsCustomHttpClient() {
    HttpClient client = HttpClient.newHttpClient();
    JavaHttpWebhookDeliverySender sender = new JavaHttpWebhookDeliverySender(client);
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

      JavaHttpWebhookDeliverySender sender = new JavaHttpWebhookDeliverySender();
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

      JavaHttpWebhookDeliverySender sender = new JavaHttpWebhookDeliverySender();
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

      JavaHttpWebhookDeliverySender sender = new JavaHttpWebhookDeliverySender();
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

      JavaHttpWebhookDeliverySender sender = new JavaHttpWebhookDeliverySender();
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

      JavaHttpWebhookDeliverySender sender = new JavaHttpWebhookDeliverySender();
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

      JavaHttpWebhookDeliverySender sender = new JavaHttpWebhookDeliverySender();
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

      JavaHttpWebhookDeliverySender sender = new JavaHttpWebhookDeliverySender();
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

    JavaHttpWebhookDeliverySender sender = new JavaHttpWebhookDeliverySender();
    assertThrows(IOException.class, () -> sender.send(delivery, endpoint));
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
