package com.aratiri.decoder.infrastructure.nostr;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class NoopNostrAdapterTest {

    final NoopNostrAdapter adapter = new NoopNostrAdapter();

    @Test
    void getLud16FromNpub_returnsCompletedFutureWithNull() {
        CompletableFuture<String> result = adapter.getLud16FromNpub("npub1...");
        assertTrue(result.isDone());
        assertNull(result.join());
    }

    @Test
    void resolveNip05ToLud16_returnsCompletedFutureWithNull() {
        CompletableFuture<String> result = adapter.resolveNip05ToLud16("user@domain.com");
        assertTrue(result.isDone());
        assertNull(result.join());
    }
}
