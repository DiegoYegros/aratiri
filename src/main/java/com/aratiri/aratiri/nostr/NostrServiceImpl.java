package com.aratiri.aratiri.nostr;

import com.aratiri.aratiri.exception.AratiriException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class NostrServiceImpl implements NostrService {

    private final NostrClient nostrClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public NostrServiceImpl(NostrClient nostrClient) {
        this.nostrClient = nostrClient;
    }

    @Override
    public CompletableFuture<String> getLud16FromNpub(String npub) {
        return nostrClient.fetchProfile(npub).thenApply(profileEvent -> {
            if (profileEvent != null && profileEvent.has("content")) {
                try {
                    String content = profileEvent.get("content").asText();
                    JsonNode contentNode = objectMapper.readTree(content);
                    if (contentNode.has("lud16")) {
                        return contentNode.get("lud16").asText();
                    }
                } catch (Exception e) {
                    throw new AratiriException("Failed to parse nostr content.",HttpStatus.INTERNAL_SERVER_ERROR);
                }
            }
            return null;
        });
    }
}
