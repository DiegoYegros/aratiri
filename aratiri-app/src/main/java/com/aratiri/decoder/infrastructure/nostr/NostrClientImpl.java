package com.aratiri.decoder.infrastructure.nostr;

import com.aratiri.core.util.NostrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class NostrClientImpl implements NostrClient {

    private static final Logger logger = LoggerFactory.getLogger(NostrClientImpl.class);

    @Value("${nostr.relay.url}")
    private String relayUrl;

    @Value("${nostr.retry.max-retries:5}")
    private int maxRetries;

    @Value("${nostr.retry.initial-delay:2000}")
    private long initialDelay;

    @Value("${nostr.retry.max-delay:300000}")
    private long maxDelay;

    private WebSocketClient client;
    private final ConcurrentHashMap<String, CompletableFuture<JsonNode>> pendingRequests = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicInteger retryCount = new AtomicInteger(0);
    private volatile boolean connected = false;

    @PostConstruct
    public void init() {
        connectWithRetry();
    }

    public void connectWithRetry() {
        if (connected) {
            return;
        }

        try {
            client = new WebSocketClient(new URI(relayUrl)) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    logger.info("Connected to Nostr relay: {}", relayUrl);
                    connected = true;
                    retryCount.set(0);
                }

                @Override
                public void onMessage(String message) {
                    try {
                        JsonNode jsonNode = objectMapper.readTree(message);
                        if (jsonNode.isArray() && jsonNode.get(0).asText().equals("EVENT")) {
                            String subscriptionId = jsonNode.get(1).asText();
                            JsonNode eventNode = jsonNode.get(2);
                            CompletableFuture<JsonNode> future = pendingRequests.remove(subscriptionId);
                            if (future != null) {
                                future.complete(eventNode);
                            }
                        }
                    } catch (Exception e) {
                        logger.error("Error processing Nostr message", e);
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    logger.warn("Disconnected from Nostr relay: {}. Reason: {}", relayUrl, reason);
                    connected = false;
                    if (retryCount.get() < maxRetries) {
                        scheduleReconnection();
                    } else {
                        logger.error("Max retries reached. Could not connect to Nostr relay.");
                    }
                }

                @Override
                public void onError(Exception ex) {
                    logger.error("Nostr WebSocket error", ex);
                    connected = false;
                }
            };

            logger.info("Attempting to connect to Nostr relay: {}", relayUrl);
            client.connect();
        } catch (Exception e) {
            logger.error("Failed to connect to Nostr relay", e);
            if (retryCount.get() < maxRetries) {
                scheduleReconnection();
            } else {
                logger.error("Got exception. Could not connect to Nostr relay. Message is: {}", e.getMessage());
            }
        }
    }

    private void scheduleReconnection() {
        long delay = (long) (initialDelay * Math.pow(2, retryCount.getAndIncrement()));
        delay = Math.min(delay, maxDelay);

        logger.info("Scheduling reconnection attempt in {} ms", delay);
        CompletableFuture.delayedExecutor(delay, TimeUnit.MILLISECONDS).execute(this::connectWithRetry);
    }

    @Scheduled(fixedDelay = 10000)
    public void checkConnection() {
        if (!connected) {
            logger.warn("Nostr client is not connected. Attempting to reconnect...");
            connectWithRetry();
        }
    }

    public CompletableFuture<JsonNode> fetchProfileByHex(String hexKey) {
        if (!connected) {
            return CompletableFuture.failedFuture(new IllegalStateException("Nostr client not connected."));
        }
        String subscriptionId = UUID.randomUUID().toString();
        String request = NostrUtil.createSubscriptionRequest(hexKey, subscriptionId);
        CompletableFuture<JsonNode> future = new CompletableFuture<JsonNode>()
                .orTimeout(10, TimeUnit.SECONDS);
        pendingRequests.put(subscriptionId, future);

        logger.info("Sending profile request for hexKey: {}, subId: {}", hexKey, subscriptionId);
        client.send(request);

        return future;
    }

    public CompletableFuture<JsonNode> fetchProfile(String npub) {
        String hexKey = NostrUtil.npubToHex(npub);
        return fetchProfileByHex(hexKey);
    }

    @PreDestroy
    public void disconnect() {
        if (client != null) {
            client.close();
        }
    }
}