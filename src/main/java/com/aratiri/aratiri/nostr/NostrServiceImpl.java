package com.aratiri.aratiri.nostr;

import com.aratiri.aratiri.exception.AratiriException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.CompletableFuture;

@AllArgsConstructor
public class NostrServiceImpl implements NostrService {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private RestTemplate restTemplate;
    private NostrClient nostrClient;
    private ObjectMapper objectMapper;

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
                    throw new AratiriException("Failed to parse nostr content.", HttpStatus.INTERNAL_SERVER_ERROR);
                }
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<String> getLud16FromPubkey(String hexKey) {
        return nostrClient.fetchProfileByHex(hexKey).thenApply(profileEvent -> {
            if (profileEvent != null && profileEvent.has("content")) {
                try {
                    String content = profileEvent.get("content").asText();
                    JsonNode contentNode = objectMapper.readTree(content);
                    if (contentNode.has("lud16")) {
                        return contentNode.get("lud16").asText();
                    }
                } catch (Exception e) {
                    throw new AratiriException("Failed to parse nostr profile content.", HttpStatus.INTERNAL_SERVER_ERROR);
                }
            }
            return null;
        });
    }

    public CompletableFuture<String> resolveNip05ToLud16(String nip05Identifier) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String[] parts = nip05Identifier.split("@");
                if (parts.length != 2) {
                    return null;
                }
                String name = parts[0];
                String domain = parts[1];

                String url = "https://" + domain + "/.well-known/nostr.json?name=" + name;
                logger.info("Fetching NIP-05 data from: {}", url);

                String response = restTemplate.getForObject(url, String.class);
                JsonNode root = objectMapper.readTree(response);

                JsonNode names = root.path("names");
                if (names.isMissingNode() || !names.has(name)) {
                    return null;
                }
                String pubkey = names.get(name).asText();
                logger.info("Found pubkey for {}: {}", nip05Identifier, pubkey);
                return getLud16FromPubkey(pubkey).join();

            } catch (Exception e) {
                logger.warn("Failed to resolve NIP-05 identifier '{}': {}", nip05Identifier, e.getMessage());
                return null;
            }
        });
    }
}
