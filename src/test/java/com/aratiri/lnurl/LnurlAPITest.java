package com.aratiri.lnurl;

import com.aratiri.auth.application.dto.UserDTO;
import com.aratiri.auth.domain.Role;
import com.aratiri.infrastructure.web.context.AratiriContext;
import com.aratiri.lnurl.application.dto.LnurlPayRequestDTO;
import com.aratiri.lnurl.application.dto.LnurlpResponseDTO;
import com.aratiri.lnurl.application.port.in.LnurlApplicationPort;
import com.aratiri.payments.application.dto.PaymentResponseDTO;
import com.aratiri.shared.exception.AratiriException;
import com.aratiri.transactions.application.dto.TransactionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LnurlAPITest {

    @Mock
    private LnurlApplicationPort lnurlPort;

    private LnurlAPI api;
    private AratiriContext ctx;

    @BeforeEach
    void setUp() {
        api = new LnurlAPI(lnurlPort);
        UserDTO user = new UserDTO("user-1", "Test", "test@test.com", Role.USER);
        ctx = new AratiriContext(user);
    }

    @Test
    void getLnurlMetadata_returnsOk() {
        LnurlpResponseDTO expected = new LnurlpResponseDTO();
        expected.setStatus("OK");
        when(lnurlPort.getLnurlMetadata("alias")).thenReturn(expected);

        ResponseEntity<LnurlpResponseDTO> response = api.getLnurlMetadata("alias");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(expected, response.getBody());
    }

    @Test
    void lnurlCallback_returnsOk() {
        Map<String, Object> expected = Map.of("pr", "lnbc1...");
        when(lnurlPort.lnurlCallback("alias", 1000L, null)).thenReturn(expected);

        ResponseEntity<Object> response = api.lnurlCallback("alias", 1000L, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(expected, response.getBody());
    }

    @Test
    void pay_returnsAccepted() {
        LnurlPayRequestDTO request = new LnurlPayRequestDTO();
        request.setCallback("https://example.com/callback");
        request.setAmountMsat(1000L);
        PaymentResponseDTO expected = PaymentResponseDTO.builder()
                .transactionId("tx-1").status(TransactionStatus.PENDING).build();
        when(lnurlPort.handlePayRequest(request, "user-1", "key-1")).thenReturn(expected);

        ResponseEntity<PaymentResponseDTO> response = api.pay("key-1", request, ctx);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertEquals(expected, response.getBody());
    }

    @Test
    void pay_throwsWhenIdempotencyKeyMissing() {
        LnurlPayRequestDTO request = new LnurlPayRequestDTO();
        request.setCallback("https://example.com/callback");

        assertThrows(AratiriException.class, () -> api.pay(null, request, ctx));
        assertThrows(AratiriException.class, () -> api.pay("", request, ctx));
    }
}
