package com.aratiri.decoder.infrastructure.nostr;

import com.aratiri.decoder.application.port.out.NostrPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

class NoopNostrAdapter implements NostrPort {

    private static final Logger logger = LoggerFactory.getLogger(NoopNostrAdapter.class);

    @Override
    public CompletableFuture<String> getLud16FromNpub(String npub) {
        logger.debug("Nostr integration is disabled. Skipping npub lookup.");
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<String> resolveNip05ToLud16(String nip05Identifier) {
        logger.debug("Nostr integration is disabled. Skipping NIP-05 resolution.");
        return CompletableFuture.completedFuture(null);
    }
}