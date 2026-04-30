package com.aratiri.decoder.infrastructure.nostr;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NostrAdapterTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private NostrClient nostrClient;

    private NostrAdapter adapter;
    private JsonMapper jsonMapper = new JsonMapper();

    @BeforeEach
    void setUp() {
        adapter = new NostrAdapter(restTemplate, nostrClient, jsonMapper);
    }

    @Test
    void getLud16FromNpub_returnsLud16FromProfile() throws Exception {
        ObjectNode contentNode = JsonNodeFactory.instance.objectNode();
        contentNode.put("lud16", "user@domain.com");
        ObjectNode profileNode = JsonNodeFactory.instance.objectNode();
        profileNode.put("content", new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(contentNode));

        when(nostrClient.fetchProfile("npub1...")).thenReturn(CompletableFuture.completedFuture(profileNode));

        CompletableFuture<String> result = adapter.getLud16FromNpub("npub1...");
        assertEquals("user@domain.com", result.join());
    }

    @Test
    void getLud16FromNpub_returnsNullWhenNoContentField() {
        ObjectNode profileNode = JsonNodeFactory.instance.objectNode();
        profileNode.put("other", "value");

        when(nostrClient.fetchProfile("npub1...")).thenReturn(CompletableFuture.completedFuture(profileNode));

        CompletableFuture<String> result = adapter.getLud16FromNpub("npub1...");
        assertNull(result.join());
    }

    @Test
    void getLud16FromNpub_returnsNullWhenProfileEventNull() {
        when(nostrClient.fetchProfile("npub1...")).thenReturn(CompletableFuture.completedFuture(null));

        CompletableFuture<String> result = adapter.getLud16FromNpub("npub1...");
        assertNull(result.join());
    }

    @Test
    void getLud16FromNpub_throwsWhenContentNotParseable() {
        ObjectNode profileNode = JsonNodeFactory.instance.objectNode();
        profileNode.put("content", "not-json");

        when(nostrClient.fetchProfile("npub1...")).thenReturn(CompletableFuture.completedFuture(profileNode));

        CompletableFuture<String> result = adapter.getLud16FromNpub("npub1...");
        assertThrows(java.util.concurrent.CompletionException.class, result::join);
    }

    @Test
    void resolveNip05ToLud16_returnsLud16() throws Exception {
        String nip05 = "user@test.com";

        ObjectNode contentNode = JsonNodeFactory.instance.objectNode();
        contentNode.put("lud16", nip05);
        ObjectNode profileNode = JsonNodeFactory.instance.objectNode();
        profileNode.put("content", new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(contentNode));

        ObjectNode nip05Response = JsonNodeFactory.instance.objectNode();
        ObjectNode names = JsonNodeFactory.instance.objectNode();
        names.put("user", "hexkey123");
        nip05Response.set("names", names);

        when(restTemplate.getForObject("https://test.com/.well-known/nostr.json?name=user", String.class))
                .thenReturn(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(nip05Response));
        when(nostrClient.fetchProfileByHex("hexkey123")).thenReturn(CompletableFuture.completedFuture(profileNode));

        CompletableFuture<String> result = adapter.resolveNip05ToLud16(nip05);
        assertEquals(nip05, result.join());
    }

    @Test
    void resolveNip05ToLud16_returnsNullForInvalidFormat() {
        String nip05 = "user"; // invalid format
        CompletableFuture<String> result = adapter.resolveNip05ToLud16(nip05);
        assertNull(result.join());
    }

    @Test
    void resolveNip05ToLud16_returnsNullWhenNamesMissing() throws Exception {
        ObjectNode nip05Response = JsonNodeFactory.instance.objectNode();
        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenReturn(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(nip05Response));

        CompletableFuture<String> result = adapter.resolveNip05ToLud16("user@test.com");
        assertNull(result.join());
    }

    @Test
    void resolveNip05ToLud16_returnsNullOnException() {
        String nip05 = "user@test.com";
        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenThrow(new RuntimeException("Network error"));

        CompletableFuture<String> result = adapter.resolveNip05ToLud16(nip05);
        assertNull(result.join());
    }
}
