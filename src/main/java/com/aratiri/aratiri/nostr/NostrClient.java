package com.aratiri.aratiri.nostr;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.concurrent.CompletableFuture;

public interface NostrClient {
    void connectWithRetry();

    CompletableFuture<JsonNode> fetchProfile(String npub);

    CompletableFuture<JsonNode> fetchProfileByHex(String hexKey);
}
