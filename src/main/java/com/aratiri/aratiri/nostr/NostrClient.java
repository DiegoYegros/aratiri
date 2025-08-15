package com.aratiri.aratiri.nostr;

import com.aratiri.aratiri.util.NostrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
public class NostrClient {

    private static final Logger logger = LoggerFactory.getLogger(NostrClient.class);

    @Value("${nostr.relay.url}")
    private String relayUrl;

    private WebSocketClient client;
    private final ConcurrentHashMap<String, CompletableFuture<JsonNode>> pendingRequests = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void connect() {
        try {
            client = new WebSocketClient(new URI(relayUrl)) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    logger.info("Connected to Nostr relay: {}", relayUrl);
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
                }

                @Override
                public void onError(Exception ex) {
                    logger.error("Nostr WebSocket error", ex);
                }
            };
            client.connect();
        } catch (Exception e) {
            logger.error("Failed to connect to Nostr relay", e);
        }
    }

    public CompletableFuture<JsonNode> fetchProfileByHex(String hexKey) {
        String subscriptionId = UUID.randomUUID().toString();
        String request = NostrUtil.createSubscriptionRequest(hexKey, subscriptionId);
        CompletableFuture<JsonNode> future = new CompletableFuture<JsonNode>()
                .orTimeout(10, TimeUnit.SECONDS);
        pendingRequests.put(subscriptionId, future);
        if (client != null && client.isOpen()) {
            logger.info("Sending profile request for hexKey: {}, subId: {}", hexKey, subscriptionId);
            client.send(request);
        } else {
            logger.error("Cannot fetch profile, Nostr client is not connected.");
            future.completeExceptionally(new IllegalStateException("Nostr client not connected."));
        }

        return future;
    }

    public CompletableFuture<JsonNode> fetchProfile(String npub) {
        String hexKey = NostrUtil.npubToHex(npub);
        String subscriptionId = UUID.randomUUID().toString();
        String request = NostrUtil.createSubscriptionRequest(hexKey, subscriptionId);
        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        pendingRequests.put(subscriptionId, future);
        client.send(request);
        return future;
    }

    @PreDestroy
    public void disconnect() {
        if (client != null) {
            client.close();
        }
    }
}
