package com.aratiri.generaldata;

import com.aratiri.generaldata.application.dto.BtcPriceHistoryResponseDTO;
import com.aratiri.generaldata.application.dto.CurrentBtcPriceResponseDTO;
import com.aratiri.generaldata.application.port.in.GeneralDataPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeneralDataAPITest {

    @Mock
    private GeneralDataPort generalDataPort;

    private GeneralDataAPI api;

    @BeforeEach
    void setUp() {
        api = new GeneralDataAPI(generalDataPort);
    }

    @Test
    void getFiatCurrencies_returnsList() {
        when(generalDataPort.getFiatCurrencies()).thenReturn(List.of("usd", "ars", "eur"));

        ResponseEntity<List<String>> response = api.getFiatCurrencies(null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(3, response.getBody().size());
        assertTrue(response.getBody().contains("usd"));
    }

    @Test
    void getCurrentBtcPrice_returnsPrice() {
        CurrentBtcPriceResponseDTO price = new CurrentBtcPriceResponseDTO(
                "usd", BigDecimal.valueOf(60000), Instant.now());
        when(generalDataPort.getCurrentBtcPrice("usd")).thenReturn(price);

        ResponseEntity<CurrentBtcPriceResponseDTO> response = api.getCurrentBtcPrice("usd", null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("usd", response.getBody().currency());
        assertEquals(BigDecimal.valueOf(60000), response.getBody().price());
        assertNotNull(response.getBody().updatedAt());
    }

    @Test
    void getBtcPriceHistory_returnsHistory() {
        BtcPriceHistoryResponseDTO history = new BtcPriceHistoryResponseDTO(
                "usd", "30d", List.of());
        when(generalDataPort.getBtcPriceHistory("usd", "30d")).thenReturn(history);

        ResponseEntity<BtcPriceHistoryResponseDTO> response = api.getBtcPriceHistory("usd", "30d", null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("usd", response.getBody().currency());
        assertEquals("30d", response.getBody().range());
        assertEquals(0, response.getBody().points().size());
    }
}
