package com.aratiri.aratiri.nostr;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

public class NostrServiceDisabled implements NostrService {

    private static final Logger logger = LoggerFactory.getLogger(NostrServiceDisabled.class);

    @Override
    public CompletableFuture<String> getLud16FromNpub(String npub) {
        logger.debug("Nostr service is disabled. Skipping npub lookup.");
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<String> getLud16FromPubkey(String hexKey) {
        logger.debug("Nostr service is disabled. Skipping pubkey lookup.");
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<String> resolveNip05ToLud16(String nip05Identifier) {
        logger.debug("Nostr service is disabled. Skipping nip-05 to lud16.");
        return CompletableFuture.completedFuture(null);

    }
}