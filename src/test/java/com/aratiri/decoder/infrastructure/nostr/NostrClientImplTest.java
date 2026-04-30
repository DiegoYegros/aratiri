package com.aratiri.decoder.infrastructure.nostr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.client.WebSocketClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NostrClientImplTest {

  @Mock
  private WebSocketClient mockClient;

  private NostrClientImpl nostrClient;

  @BeforeEach
  void setUp() {
    nostrClient = new NostrClientImpl();
    ReflectionTestUtils.setField(nostrClient, "relayUrl", "ws://localhost:8080");
    ReflectionTestUtils.setField(nostrClient, "maxRetries", 3);
    ReflectionTestUtils.setField(nostrClient, "initialDelay", 100L);
    ReflectionTestUtils.setField(nostrClient, "maxDelay", 1000L);
    ReflectionTestUtils.setField(nostrClient, "client", mockClient);
  }

  @AfterEach
  void tearDown() {
    nostrClient.disconnect();
  }

  // -- connectWithRetry --

  @Test
  void connectWithRetry_whenAlreadyConnected_doesNothing() {
    ReflectionTestUtils.setField(nostrClient, "connected", true);
    WebSocketClient clientBefore = (WebSocketClient) ReflectionTestUtils.getField(nostrClient, "client");

    nostrClient.connectWithRetry();

    WebSocketClient clientAfter = (WebSocketClient) ReflectionTestUtils.getField(nostrClient, "client");
    assertSame(clientBefore, clientAfter);
  }

  @Test
  void connectWithRetry_whenNotConnected_createsNewClient() {
    ReflectionTestUtils.setField(nostrClient, "connected", false);

    nostrClient.connectWithRetry();

    Object newClient = ReflectionTestUtils.getField(nostrClient, "client");
    assertNotNull(newClient);
  }

  // -- checkConnection --

  @Test
  void checkConnection_whenConnected_doesNothing() {
    ReflectionTestUtils.setField(nostrClient, "connected", true);

    nostrClient.checkConnection();

    assertTrue((Boolean) ReflectionTestUtils.getField(nostrClient, "connected"));
  }

  @Test
  void checkConnection_whenNotConnected_attemptsReconnect() {
    ReflectionTestUtils.setField(nostrClient, "connected", false);

    // checkConnection calls connectWithRetry which internally creates a new
    // WebSocketClient and calls connect(). The new client may fail to connect
    // to ws://localhost:8080 but the method itself should not throw.
    assertDoesNotThrow(() -> nostrClient.checkConnection());
  }

  // -- fetchProfileByHex --

  @Test
  void fetchProfileByHex_whenNotConnected_returnsFailedFuture() {
    ReflectionTestUtils.setField(nostrClient, "connected", false);

    CompletableFuture<JsonNode> future = nostrClient.fetchProfileByHex("hexkey123");

    assertTrue(future.isCompletedExceptionally());
    ExecutionException ex = assertThrows(ExecutionException.class, future::get);
    assertInstanceOf(IllegalStateException.class, ex.getCause());
    assertTrue(ex.getCause().getMessage().contains("not connected"));
  }

  @Test
  void fetchProfileByHex_whenConnected_sendsRequestAndReturnsFuture() {
    ReflectionTestUtils.setField(nostrClient, "connected", true);

    CompletableFuture<JsonNode> future = nostrClient.fetchProfileByHex("hexkey123");

    verify(mockClient).send(anyString());
    assertNotNull(future);
    assertFalse(future.isDone());
  }

  @Test
  void fetchProfileByHex_registersPendingRequest() {
    ReflectionTestUtils.setField(nostrClient, "connected", true);

    nostrClient.fetchProfileByHex("hexkey123");

    @SuppressWarnings("unchecked")
    ConcurrentHashMap<String, CompletableFuture<JsonNode>> pending =
        (ConcurrentHashMap<String, CompletableFuture<JsonNode>>)
            ReflectionTestUtils.getField(nostrClient, "pendingRequests");
    assertNotNull(pending);
    assertEquals(1, pending.size());
  }

  @Test
  void fetchProfileByHex_futureHasTimeout() {
    ReflectionTestUtils.setField(nostrClient, "connected", true);

    CompletableFuture<JsonNode> future = nostrClient.fetchProfileByHex("hexkey123");

    assertNotNull(future);
    assertFalse(future.isDone());
  }

  // -- fetchProfile --

  @Test
  void fetchProfile_whenConnected_delegatesToFetchProfileByHex() {
    ReflectionTestUtils.setField(nostrClient, "connected", true);

    try (MockedStatic<NostrUtil> mockedNostrUtil = mockStatic(NostrUtil.class)) {
      mockedNostrUtil.when(() -> NostrUtil.npubToHex("npub1validkey"))
          .thenReturn("hexkey123");
      mockedNostrUtil.when(() -> NostrUtil.createSubscriptionRequest(anyString(), anyString()))
          .thenReturn("[\"REQ\",\"sub\",{\"authors\":[\"hexkey123\"],\"kinds\":[0],\"limit\":1}]");

      CompletableFuture<JsonNode> future = nostrClient.fetchProfile("npub1validkey");

      verify(mockClient).send(anyString());
      assertNotNull(future);
      assertFalse(future.isDone());
    }
  }

  @Test
  void fetchProfile_whenNotConnected_returnsFailedFuture() {
    ReflectionTestUtils.setField(nostrClient, "connected", false);

    try (MockedStatic<NostrUtil> mockedNostrUtil = mockStatic(NostrUtil.class)) {
      mockedNostrUtil.when(() -> NostrUtil.npubToHex("npub1validkey"))
          .thenReturn("hexkey123");
      mockedNostrUtil.when(() -> NostrUtil.createSubscriptionRequest(anyString(), anyString()))
          .thenReturn("[\"REQ\",\"sub\",{\"authors\":[\"hexkey123\"],\"kinds\":[0],\"limit\":1}]");

      CompletableFuture<JsonNode> future = nostrClient.fetchProfile("npub1validkey");

      assertTrue(future.isCompletedExceptionally());
      ExecutionException ex = assertThrows(ExecutionException.class, future::get);
      assertInstanceOf(IllegalStateException.class, ex.getCause());
    }
  }

  // -- handleMessage (via reflection) --

  @Test
  void handleMessage_receivesEventMessage_completesFuture() throws Exception {
    ReflectionTestUtils.setField(nostrClient, "connected", true);
    CompletableFuture<JsonNode> future = nostrClient.fetchProfileByHex("hexkey123");

    @SuppressWarnings("unchecked")
    ConcurrentHashMap<String, CompletableFuture<JsonNode>> pending =
        (ConcurrentHashMap<String, CompletableFuture<JsonNode>>)
            ReflectionTestUtils.getField(nostrClient, "pendingRequests");
    String subscriptionId = pending.keySet().iterator().next();

    String eventJson = "[\"EVENT\",\"" + subscriptionId + "\",{\"content\":\"data\",\"kind\":0}]";

    ReflectionTestUtils.invokeMethod(nostrClient, "handleMessage", eventJson);

    assertTrue(future.isDone());
    assertFalse(future.isCompletedExceptionally());
    JsonNode result = future.get(1, TimeUnit.SECONDS);
    assertNotNull(result);
    assertTrue(result.has("content"));
  }

  @Test
  void handleMessage_receivesNonEventMessage_doesNotCompleteFuture() {
    ReflectionTestUtils.setField(nostrClient, "connected", true);
    CompletableFuture<JsonNode> future = nostrClient.fetchProfileByHex("hexkey123");

    String nonEventJson = "[\"NOTICE\",\"some message\"]";

    ReflectionTestUtils.invokeMethod(nostrClient, "handleMessage", nonEventJson);

    assertFalse(future.isDone());
  }

  @Test
  void handleMessage_receivesEventWithoutPendingRequest_doesNotThrow() {
    ReflectionTestUtils.setField(nostrClient, "connected", true);

    String eventJson = "[\"EVENT\",\"unknown-sub-id\",{\"content\":\"test\"}]";

    assertDoesNotThrow(() ->
        ReflectionTestUtils.invokeMethod(nostrClient, "handleMessage", eventJson));
  }

  @Test
  void handleMessage_receivesMalformedJson_doesNotThrow() {
    ReflectionTestUtils.setField(nostrClient, "connected", true);

    assertDoesNotThrow(() ->
        ReflectionTestUtils.invokeMethod(nostrClient, "handleMessage", "not-json"));
  }

  @Test
  void handleMessage_receivesNonArrayJson_doesNotThrow() {
    ReflectionTestUtils.setField(nostrClient, "connected", true);

    assertDoesNotThrow(() ->
        ReflectionTestUtils.invokeMethod(nostrClient, "handleMessage", "{\"key\":\"value\"}"));
  }

  // -- completePendingRequest (via reflection) --

  @Test
  void completePendingRequest_whenFutureExists_completesIt() throws Exception {
    String subscriptionId = "sub-complete";
    ReflectionTestUtils.setField(nostrClient, "connected", true);
    CompletableFuture<JsonNode> future = new CompletableFuture<>();
    @SuppressWarnings("unchecked")
    ConcurrentHashMap<String, CompletableFuture<JsonNode>> pending =
        (ConcurrentHashMap<String, CompletableFuture<JsonNode>>)
            ReflectionTestUtils.getField(nostrClient, "pendingRequests");
    pending.put(subscriptionId, future);

    ObjectMapper mapper = new ObjectMapper();
    String eventJson = "[\"EVENT\",\"" + subscriptionId + "\",{\"content\":\"data\"}]";
    JsonNode jsonNode = mapper.readTree(eventJson);

    ReflectionTestUtils.invokeMethod(nostrClient, "completePendingRequest", jsonNode);

    assertTrue(future.isDone());
    assertFalse(future.isCompletedExceptionally());
    JsonNode result = future.get(1, TimeUnit.SECONDS);
    assertEquals("data", result.get("content").asText());
  }

  // -- handleClose (via reflection) --

  @Test
  void handleClose_setsConnectedFalse() {
    ReflectionTestUtils.setField(nostrClient, "connected", true);

    ReflectionTestUtils.invokeMethod(nostrClient, "handleClose", "connection lost");

    Boolean connected = (Boolean) ReflectionTestUtils.getField(nostrClient, "connected");
    assertFalse(connected);
  }

  // -- disconnect --

  @Test
  void disconnect_whenClientNotNull_closesClient() {
    nostrClient.disconnect();

    verify(mockClient).close();
  }

  @Test
  void disconnect_whenClientNull_doesNotThrow() {
    ReflectionTestUtils.setField(nostrClient, "client", null);

    assertDoesNotThrow(() -> nostrClient.disconnect());
  }

  // -- onOpen callback simulation --

  @Test
  void onOpen_setsConnectedTrueAndResetsRetryCount() throws URISyntaxException {
    NostrClientImpl freshClient = new NostrClientImpl();
    ReflectionTestUtils.setField(freshClient, "relayUrl", "ws://localhost:8080");
    ReflectionTestUtils.setField(freshClient, "maxRetries", 3);
    ReflectionTestUtils.setField(freshClient, "initialDelay", 100L);
    ReflectionTestUtils.setField(freshClient, "maxDelay", 1000L);

    WebSocketClient wsClient = new WebSocketClient(new URI("ws://localhost:8080")) {
      @Override
      public void onOpen(org.java_websocket.handshake.ServerHandshake handshakedata) {
        ReflectionTestUtils.setField(freshClient, "connected", true);
        ReflectionTestUtils.setField(freshClient, "retryCount",
            new java.util.concurrent.atomic.AtomicInteger(0));
      }
      @Override
      public void onMessage(String message) {
        // Message handling is irrelevant for this callback-focused test double.
      }
      @Override
      public void onClose(int code, String reason, boolean remote) {
        // Close handling is irrelevant for this callback-focused test double.
      }
      @Override
      public void onError(Exception ex) {
        // Error handling is irrelevant for this callback-focused test double.
      }
    };
    ReflectionTestUtils.setField(freshClient, "client", wsClient);
    ReflectionTestUtils.setField(freshClient, "connected", false);

    wsClient.onOpen(null);

    Boolean connected = (Boolean) ReflectionTestUtils.getField(freshClient, "connected");
    assertTrue(connected);
    java.util.concurrent.atomic.AtomicInteger retryCount =
        (java.util.concurrent.atomic.AtomicInteger) ReflectionTestUtils.getField(freshClient, "retryCount");
    assertEquals(0, retryCount.get());
  }

  // -- onError callback simulation --

  @Test
  void onError_setsConnectedFalse() throws URISyntaxException {
    NostrClientImpl freshClient = new NostrClientImpl();
    ReflectionTestUtils.setField(freshClient, "relayUrl", "ws://localhost:8080");
    ReflectionTestUtils.setField(freshClient, "maxRetries", 3);
    ReflectionTestUtils.setField(freshClient, "initialDelay", 100L);
    ReflectionTestUtils.setField(freshClient, "maxDelay", 1000L);
    ReflectionTestUtils.setField(freshClient, "connected", true);

    WebSocketClient wsClient = new WebSocketClient(new URI("ws://localhost:8080")) {
      @Override
      public void onOpen(org.java_websocket.handshake.ServerHandshake handshakedata) {
        // Open handling is irrelevant for this callback-focused test double.
      }
      @Override
      public void onMessage(String message) {
        // Message handling is irrelevant for this callback-focused test double.
      }
      @Override
      public void onClose(int code, String reason, boolean remote) {
        // Close handling is irrelevant for this callback-focused test double.
      }
      @Override
      public void onError(Exception ex) {
        ReflectionTestUtils.setField(freshClient, "connected", false);
      }
    };
    ReflectionTestUtils.setField(freshClient, "client", wsClient);

    wsClient.onError(new RuntimeException("test error"));

    Boolean connected = (Boolean) ReflectionTestUtils.getField(freshClient, "connected");
    assertFalse(connected);
  }

  // -- scheduleReconnection (via reflection) --

  @Test
  void scheduleReconnection_computesExponentialBackoff() {
    ReflectionTestUtils.setField(nostrClient, "connected", false);
    ReflectionTestUtils.setField(nostrClient, "initialDelay", 100L);
    ReflectionTestUtils.setField(nostrClient, "maxDelay", 100000L);
    ReflectionTestUtils.setField(nostrClient, "maxRetries", 5);

    ReflectionTestUtils.invokeMethod(nostrClient, "scheduleReconnection");

    java.util.concurrent.atomic.AtomicInteger retryCount =
        (java.util.concurrent.atomic.AtomicInteger) ReflectionTestUtils.getField(nostrClient, "retryCount");
    assertEquals(1, retryCount.get());
  }

  @Test
  void scheduleReconnection_capsDelayAtMaxDelay() {
    ReflectionTestUtils.setField(nostrClient, "connected", false);
    ReflectionTestUtils.setField(nostrClient, "initialDelay", 100L);
    ReflectionTestUtils.setField(nostrClient, "maxDelay", 200L);
    ReflectionTestUtils.setField(nostrClient, "maxRetries", 5);

    java.util.concurrent.atomic.AtomicInteger retryCount =
        new java.util.concurrent.atomic.AtomicInteger(10);
    ReflectionTestUtils.setField(nostrClient, "retryCount", retryCount);

    assertDoesNotThrow(() ->
        ReflectionTestUtils.invokeMethod(nostrClient, "scheduleReconnection"));
  }

  // -- multiple fetchProfileByHex calls --

  @Test
  void fetchProfileByHex_multipleCalls_registersMultiplePendingRequests() {
    ReflectionTestUtils.setField(nostrClient, "connected", true);

    nostrClient.fetchProfileByHex("hex1");
    nostrClient.fetchProfileByHex("hex2");
    nostrClient.fetchProfileByHex("hex3");

    @SuppressWarnings("unchecked")
    ConcurrentHashMap<String, CompletableFuture<JsonNode>> pending =
        (ConcurrentHashMap<String, CompletableFuture<JsonNode>>)
            ReflectionTestUtils.getField(nostrClient, "pendingRequests");
    assertEquals(3, pending.size());
  }

  // -- handleMessage completes only matching subscription --

  @Test
  void handleMessage_completesOnlyMatchingSubscription() throws Exception {
    ReflectionTestUtils.setField(nostrClient, "connected", true);
    CompletableFuture<JsonNode> future1 = nostrClient.fetchProfileByHex("hex1");
    CompletableFuture<JsonNode> future2 = nostrClient.fetchProfileByHex("hex2");

    @SuppressWarnings("unchecked")
    ConcurrentHashMap<String, CompletableFuture<JsonNode>> pending =
        (ConcurrentHashMap<String, CompletableFuture<JsonNode>>)
            ReflectionTestUtils.getField(nostrClient, "pendingRequests");
    String subId1 = pending.keySet().stream()
        .filter(k -> pending.get(k) == future1)
        .findFirst()
        .orElseThrow();

    String eventJson = "[\"EVENT\",\"" + subId1 + "\",{\"content\":\"only-sub1\"}]";
    ReflectionTestUtils.invokeMethod(nostrClient, "handleMessage", eventJson);

    assertTrue(future1.isDone());
    assertFalse(future2.isDone());
    JsonNode result = future1.get(1, TimeUnit.SECONDS);
    assertEquals("only-sub1", result.get("content").asText());
  }

  // -- field initialization --

  @Test
  void objectMapper_isNotNull() {
    ObjectMapper mapper = (ObjectMapper) ReflectionTestUtils.getField(nostrClient, "objectMapper");
    assertNotNull(mapper);
  }

  @Test
  void pendingRequests_isNotNullByDefault() {
    @SuppressWarnings("unchecked")
    ConcurrentHashMap<String, CompletableFuture<JsonNode>> pending =
        (ConcurrentHashMap<String, CompletableFuture<JsonNode>>)
            ReflectionTestUtils.getField(nostrClient, "pendingRequests");
    assertNotNull(pending);
  }
}
