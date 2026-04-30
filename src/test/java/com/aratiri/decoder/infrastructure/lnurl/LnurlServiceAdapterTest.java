package com.aratiri.decoder.infrastructure.lnurl;

import com.aratiri.lnurl.application.dto.LnurlpResponseDTO;
import com.aratiri.lnurl.application.port.in.LnurlApplicationPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LnurlServiceAdapterTest {

    @Mock
    private LnurlApplicationPort lnurlApplicationPort;

    private LnurlServiceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new LnurlServiceAdapter(lnurlApplicationPort);
    }

    @Test
    void getInternalMetadata_delegatesToPort() {
        LnurlpResponseDTO expected = new LnurlpResponseDTO();
        expected.setStatus("OK");
        when(lnurlApplicationPort.getLnurlMetadata("alias")).thenReturn(expected);

        LnurlpResponseDTO result = adapter.getInternalMetadata("alias");

        assertEquals(expected, result);
    }

    @Test
    void getExternalMetadata_delegatesToPort() {
        LnurlpResponseDTO expected = new LnurlpResponseDTO();
        expected.setCallback("https://ext.com/callback");
        when(lnurlApplicationPort.getExternalLnurlMetadata("https://ext.com")).thenReturn(expected);

        LnurlpResponseDTO result = adapter.getExternalMetadata("https://ext.com");

        assertEquals(expected, result);
    }
}
