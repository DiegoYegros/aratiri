package com.aratiri.aratiri.nostr;

import java.util.concurrent.CompletableFuture;

public interface NostrService {
    CompletableFuture<String> getLud16FromNpub(String npub);

    CompletableFuture<String> getLud16FromPubkey(String hexKey);

    CompletableFuture<String> resolveNip05ToLud16(String nip05Identifier);
}
