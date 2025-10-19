package com.aratiri.decoder.application.port.out;

import java.util.concurrent.CompletableFuture;

public interface NostrPort {

    CompletableFuture<String> getLud16FromNpub(String npub);

    CompletableFuture<String> resolveNip05ToLud16(String nip05Identifier);
}
