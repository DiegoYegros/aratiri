package com.aratiri.payments.application.dto;

import com.aratiri.transactions.application.dto.TransactionStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PaymentResponseDTOTest {

    @Test
    void builder_storesAllFields() {
        PaymentResponseDTO dto = PaymentResponseDTO.builder()
                .transactionId("tx-1")
                .status(TransactionStatus.PENDING)
                .message("Payment initiated")
                .build();

        assertEquals("tx-1", dto.getTransactionId());
        assertEquals(TransactionStatus.PENDING, dto.getStatus());
        assertEquals("Payment initiated", dto.getMessage());
    }

    @Test
    void builder_withNull() {
        PaymentResponseDTO dto = PaymentResponseDTO.builder()
                .transactionId("tx-1")
                .status(TransactionStatus.FAILED)
                .message(null)
                .build();

        assertEquals("tx-1", dto.getTransactionId());
        assertEquals(TransactionStatus.FAILED, dto.getStatus());
        assertNull(dto.getMessage());
    }
}
