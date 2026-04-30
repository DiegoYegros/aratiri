package com.aratiri.lnurl.application.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LnurlDTOsTest {

    @Test
    void lnurlCallbackResponseDTO_allFields() {
        LnurlCallbackResponseDTO dto = new LnurlCallbackResponseDTO();
        dto.setPaymentRequest("lnbc1...");
        dto.setRoutes(java.util.List.of());

        assertEquals("lnbc1...", dto.getPaymentRequest());
        assertEquals(0, dto.getRoutes().size());
    }

    @Test
    void lnurlPayRequestDTO_allFields() {
        LnurlPayRequestDTO dto = new LnurlPayRequestDTO();
        dto.setCallback("https://example.com/lnurl/callback");
        dto.setAmountMsat(10_000L);
        dto.setComment("test comment");

        assertEquals("https://example.com/lnurl/callback", dto.getCallback());
        assertEquals(10_000L, dto.getAmountMsat());
        assertEquals("test comment", dto.getComment());
    }

    @Test
    void lnurlpResponseDTO_allFields() {
        LnurlpResponseDTO dto = new LnurlpResponseDTO();
        dto.setTag("payRequest");
        dto.setStatus("OK");
        dto.setAllowsNostr(true);
        dto.setNostrPubkey("npub1...");
        dto.setCallback("https://example.com/callback");
        dto.setMinSendable(1_000L);
        dto.setMaxSendable(100_000_000L);
        dto.setMetadata("[[\"text/plain\",\"pay to test\"]]");
        dto.setCommentAllowed(300);

        assertEquals("payRequest", dto.getTag());
        assertEquals("OK", dto.getStatus());
        assertTrue(dto.getAllowsNostr());
        assertEquals("npub1...", dto.getNostrPubkey());
        assertEquals("https://example.com/callback", dto.getCallback());
        assertEquals(1_000L, dto.getMinSendable());
        assertEquals(100_000_000L, dto.getMaxSendable());
        assertEquals("[[\"text/plain\",\"pay to test\"]]", dto.getMetadata());
        assertEquals(300, dto.getCommentAllowed());
    }
}
