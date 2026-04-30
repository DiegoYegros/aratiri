package com.aratiri.payments;

import com.aratiri.auth.application.dto.UserDTO;
import com.aratiri.auth.domain.Role;
import com.aratiri.infrastructure.web.context.AratiriContext;
import com.aratiri.payments.application.dto.OnChainPaymentDTOs;
import com.aratiri.payments.application.dto.PayInvoiceRequestDTO;
import com.aratiri.payments.application.dto.PaymentResponseDTO;
import com.aratiri.payments.application.port.in.PaymentsPort;
import com.aratiri.shared.exception.AratiriException;
import com.aratiri.transactions.application.dto.TransactionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentsAPITest {

    @Mock
    private PaymentsPort paymentsPort;

    private PaymentsAPI api;
    private AratiriContext ctx;

    @BeforeEach
    void setUp() {
        api = new PaymentsAPI(paymentsPort);
        UserDTO user = new UserDTO("user-1", "Test", "test@test.com", Role.USER);
        ctx = new AratiriContext(user);
    }

    @Test
    void payInvoice_returnsAccepted() {
        PayInvoiceRequestDTO request = new PayInvoiceRequestDTO();
        request.setInvoice("lnbc1...");
        PaymentResponseDTO expected = PaymentResponseDTO.builder()
                .transactionId("tx-1").status(TransactionStatus.PENDING).build();
        when(paymentsPort.payLightningInvoice(any(), eq("user-1"), eq("key-1"))).thenReturn(expected);

        ResponseEntity<PaymentResponseDTO> response = api.payInvoice("key-1", request, ctx);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertEquals(expected, response.getBody());
    }

    @Test
    void payInvoice_throwsWhenIdempotencyKeyMissing() {
        PayInvoiceRequestDTO request = new PayInvoiceRequestDTO();
        request.setInvoice("lnbc1...");

        assertThrows(AratiriException.class, () -> api.payInvoice(null, request, ctx));
        assertThrows(AratiriException.class, () -> api.payInvoice("", request, ctx));
        assertThrows(AratiriException.class, () -> api.payInvoice("   ", request, ctx));
    }

    @Test
    void sendOnChain_returnsAccepted() {
        OnChainPaymentDTOs.SendOnChainRequestDTO request = new OnChainPaymentDTOs.SendOnChainRequestDTO();
        request.setAddress("bc1q...");
        request.setSatsAmount(10000L);
        OnChainPaymentDTOs.SendOnChainResponseDTO expected = new OnChainPaymentDTOs.SendOnChainResponseDTO();
        expected.setTransactionId("tx-1");
        expected.setTransactionStatus(TransactionStatus.PENDING);
        when(paymentsPort.sendOnChain(any(), eq("user-1"), eq("key-1"))).thenReturn(expected);

        ResponseEntity<OnChainPaymentDTOs.SendOnChainResponseDTO> response = api.sendOnChain("key-1", request, ctx);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertEquals(expected, response.getBody());
    }

    @Test
    void sendOnChain_throwsWhenIdempotencyKeyMissing() {
        OnChainPaymentDTOs.SendOnChainRequestDTO request = new OnChainPaymentDTOs.SendOnChainRequestDTO();
        request.setAddress("bc1q...");
        request.setSatsAmount(10000L);

        assertThrows(AratiriException.class, () -> api.sendOnChain(null, request, ctx));
    }

    @Test
    void estimateOnChainFee_returnsOk() {
        OnChainPaymentDTOs.EstimateFeeRequestDTO request = new OnChainPaymentDTOs.EstimateFeeRequestDTO();
        request.setAddress("bc1q...");
        request.setSatsAmount(10000L);
        OnChainPaymentDTOs.EstimateFeeResponseDTO expected = new OnChainPaymentDTOs.EstimateFeeResponseDTO();
        expected.setFeeSat(200L);
        expected.setSatPerVbyte(10L);
        when(paymentsPort.estimateOnChainFee(any(), eq("user-1"))).thenReturn(expected);

        ResponseEntity<OnChainPaymentDTOs.EstimateFeeResponseDTO> response = api.estimateOnChainFee(request, ctx);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(expected, response.getBody());
    }
}
