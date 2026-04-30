package com.aratiri.decoder;

import com.aratiri.decoder.application.dto.DecodedResultDTO;
import com.aratiri.decoder.application.port.in.DecoderPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DecoderAPITest {

    @Mock
    private DecoderPort decoderPort;

    private DecoderAPI api;

    @BeforeEach
    void setUp() {
        api = new DecoderAPI(decoderPort);
    }

    @Test
    void decode_returnsOkWithResult() {
        DecodedResultDTO expected = DecodedResultDTO.builder()
                .type("lightning_invoice")
                .data("decoded-data")
                .build();
        when(decoderPort.decode("lnbc1...")).thenReturn(expected);

        ResponseEntity<DecodedResultDTO> response = api.decode("lnbc1...");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("lightning_invoice", response.getBody().getType());
        assertEquals("decoded-data", response.getBody().getData());
    }

    @Test
    void decode_returnsErrorType() {
        DecodedResultDTO expected = DecodedResultDTO.builder()
                .type("error")
                .error("Invalid input")
                .build();
        when(decoderPort.decode("invalid")).thenReturn(expected);

        ResponseEntity<DecodedResultDTO> response = api.decode("invalid");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("error", response.getBody().getType());
        assertEquals("Invalid input", response.getBody().getError());
    }
}
