package com.aratiri.transactions;

import com.aratiri.auth.application.dto.UserDTO;
import com.aratiri.auth.domain.Role;
import com.aratiri.infrastructure.web.context.AratiriContext;
import com.aratiri.shared.exception.AratiriException;
import com.aratiri.transactions.application.dto.TransactionDTOResponse;
import com.aratiri.transactions.application.dto.TransactionPageResponse;
import com.aratiri.transactions.application.port.in.TransactionsPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionsAPITest {

    @Mock
    private TransactionsPort transactionsPort;

    private TransactionsAPI api;
    private AratiriContext ctx;

    @BeforeEach
    void setUp() {
        api = new TransactionsAPI(transactionsPort);
        UserDTO user = new UserDTO("user-1", "Test", "test@test.com", Role.USER);
        ctx = new AratiriContext(user);
    }

    @Test
    void getTransaction_returnsTransactionWhenFound() {
        TransactionDTOResponse dto = TransactionDTOResponse.builder().id("tx-1").build();
        when(transactionsPort.getTransactionById("tx-1", "user-1")).thenReturn(Optional.of(dto));

        ResponseEntity<TransactionDTOResponse> response = api.getTransaction("tx-1", ctx);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(dto, response.getBody());
    }

    @Test
    void getTransaction_throwsWhenNotFound() {
        when(transactionsPort.getTransactionById("tx-1", "user-1")).thenReturn(Optional.empty());

        assertThrows(AratiriException.class, () -> api.getTransaction("tx-1", ctx));
    }

    @Test
    void listTransactions_returnsPage() {
        TransactionPageResponse page = TransactionPageResponse.builder().build();
        when(transactionsPort.getTransactionsWithCursor("user-1", null, 50)).thenReturn(page);

        ResponseEntity<TransactionPageResponse> response = api.listTransactions(null, 50, ctx);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(page, response.getBody());
    }

    @Test
    void listTransactions_validatesCursor() {
        assertThrows(AratiriException.class, () -> api.listTransactions("invalid", 50, ctx));
        assertThrows(AratiriException.class, () -> api.listTransactions("_id", 50, ctx));
        assertThrows(AratiriException.class, () -> api.listTransactions("abc_id", 50, ctx));
    }

    @Test
    void listTransactions_acceptsValidCursor() {
        TransactionPageResponse page = TransactionPageResponse.builder().build();
        when(transactionsPort.getTransactionsWithCursor("user-1", "12345_txid", 50)).thenReturn(page);

        ResponseEntity<TransactionPageResponse> response = api.listTransactions("12345_txid", 50, ctx);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }
}
