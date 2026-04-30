package com.aratiri.decoder.infrastructure.nostr;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class NoopNostrClientTest {

    final NoopNostrClient client = new NoopNostrClient();

    @Test
    void connectWithRetry_doesNothing() {
        assertDoesNotThrow(client::connectWithRetry);
    }

    @Test
    void fetchProfile_returnsFailedFuture() {
        CompletableFuture<?> future = client.fetchProfile("npub1...");
        assertTrue(future.isCompletedExceptionally());
        future.exceptionally(ex -> {
            assertTrue(ex instanceof IllegalArgumentException);
            assertTrue(ex.getMessage().contains("disabled"));
            return null;
        }).join();
    }

    @Test
    void fetchProfileByHex_returnsFailedFuture() {
        CompletableFuture<?> future = client.fetchProfileByHex("hex...");
        assertTrue(future.isCompletedExceptionally());
        future.exceptionally(ex -> {
            assertTrue(ex instanceof IllegalArgumentException);
            assertTrue(ex.getMessage().contains("disabled"));
            return null;
        }).join();
    }
}
