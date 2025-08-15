package com.aratiri.aratiri.nostr;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.concurrent.CompletableFuture;

public class NostrClientDisabled implements NostrClient {
    @Override
    public void connectWithRetry() {
    }

    @Override
    public CompletableFuture<JsonNode> fetchProfile(String npub) {
        return CompletableFuture.failedFuture(new IllegalArgumentException("Nostr Client is disabled."));
    }

    @Override
    public CompletableFuture<JsonNode> fetchProfileByHex(String hexKey) {
        return CompletableFuture.failedFuture(new IllegalArgumentException("Nostr Client is disabled."));
    }
}
