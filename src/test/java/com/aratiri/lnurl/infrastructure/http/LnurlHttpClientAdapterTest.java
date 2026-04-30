package com.aratiri.lnurl.infrastructure.http;

import com.aratiri.lnurl.application.dto.LnurlCallbackResponseDTO;
import com.aratiri.lnurl.application.dto.LnurlpResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LnurlHttpClientAdapterTest {

    @Mock
    private RestTemplate restTemplate;

    private LnurlHttpClientAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new LnurlHttpClientAdapter(restTemplate);
    }

    @Test
    void fetchMetadata_returnsResponse() {
        LnurlpResponseDTO expected = new LnurlpResponseDTO();
        expected.setCallback("https://example.com/callback");
        when(restTemplate.getForObject("https://example.com/lnurl", LnurlpResponseDTO.class))
                .thenReturn(expected);

        LnurlpResponseDTO result = adapter.fetchMetadata("https://example.com/lnurl");

        assertEquals(expected, result);
    }

    @Test
    void fetchCallbackInvoice_returnsResponse() {
        LnurlCallbackResponseDTO expected = new LnurlCallbackResponseDTO();
        expected.setPaymentRequest("lnbc1...");
        when(restTemplate.getForObject("https://example.com/callback?amount=1000", LnurlCallbackResponseDTO.class))
                .thenReturn(expected);

        LnurlCallbackResponseDTO result = adapter.fetchCallbackInvoice("https://example.com/callback?amount=1000");

        assertEquals(expected, result);
    }
}
