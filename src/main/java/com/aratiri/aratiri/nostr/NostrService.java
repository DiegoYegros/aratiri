package com.aratiri.aratiri.nostr;

import java.util.concurrent.CompletableFuture;

public interface NostrService {
    CompletableFuture<String> getLud16FromNpub(String npub);
}
