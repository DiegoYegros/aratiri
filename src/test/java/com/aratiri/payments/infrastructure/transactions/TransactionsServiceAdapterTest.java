package com.aratiri.payments.infrastructure.transactions;

import com.aratiri.transactions.application.dto.CreateTransactionRequest;
import com.aratiri.transactions.application.dto.TransactionDTOResponse;
import com.aratiri.transactions.application.dto.TransactionStatus;
import com.aratiri.transactions.application.port.in.TransactionsPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionsServiceAdapterTest {

    @Mock
    private TransactionsPort transactionsPort;

    private TransactionsServiceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new TransactionsServiceAdapter(transactionsPort);
    }

    @Test
    void createTransaction_delegates() {
        CreateTransactionRequest request = CreateTransactionRequest.builder()
                .userId("u-1")
                .amountSat(1000L)
                .build();
        TransactionDTOResponse expected = TransactionDTOResponse.builder()
                .id("tx-1")
                .status(TransactionStatus.PENDING)
                .build();
        when(transactionsPort.createTransaction(request)).thenReturn(expected);

        TransactionDTOResponse result = adapter.createTransaction(request);

        assertEquals("tx-1", result.getId());
    }

    @Test
    void confirmTransaction_delegates() {
        adapter.confirmTransaction("tx-1", "u-1");
        verify(transactionsPort).confirmTransaction("tx-1", "u-1");
    }

    @Test
    void failTransaction_delegates() {
        adapter.failTransaction("tx-1", "insufficient funds");
        verify(transactionsPort).failTransaction("tx-1", "insufficient funds");
    }

    @Test
    void addFeeToTransaction_delegates() {
        adapter.addFeeToTransaction("tx-1", 100L);
        verify(transactionsPort).addFeeToTransaction("tx-1", 100L);
    }
}
