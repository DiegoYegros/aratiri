package com.aratiri.accounts;

import com.aratiri.accounts.application.dto.*;
import com.aratiri.accounts.application.port.in.AccountsPort;
import com.aratiri.infrastructure.web.context.AratiriContext;
import com.aratiri.auth.application.dto.UserDTO;
import com.aratiri.auth.domain.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountsAPITest {

    @Mock
    private AccountsPort accountsPort;

    private AccountsAPI api;
    private final AratiriContext ctx = new AratiriContext(
            new UserDTO("user-1", "Test", "test@example.com", Role.USER));

    @BeforeEach
    void setUp() {
        api = new AccountsAPI(accountsPort);
    }

    @Test
    void getAccount_returnsAccount() {
        AccountDTO dto = AccountDTO.builder()
                .id("acc-1")
                .userId("user-1")
                .balance(0L)
                .build();
        when(accountsPort.getAccountByUserId("user-1")).thenReturn(dto);

        ResponseEntity<AccountDTO> response = api.getAccount(ctx);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("acc-1", response.getBody().getId());
    }

    @Test
    void getAccountById_returnsAccount() {
        AccountDTO dto = AccountDTO.builder()
                .id("acc-1")
                .userId("user-1")
                .balance(50000L)
                .build();
        when(accountsPort.getAccount("acc-1")).thenReturn(dto);

        ResponseEntity<AccountDTO> response = api.getAccountById("acc-1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(50000L, response.getBody().getBalance());
    }

    @Test
    void getAccountByUserId_returnsAccount() {
        AccountDTO dto = AccountDTO.builder()
                .id("acc-1")
                .userId("user-1")
                .balance(0L)
                .build();
        when(accountsPort.getAccountByUserId("user-1")).thenReturn(dto);

        ResponseEntity<AccountDTO> response = api.getAccountByUserId("user-1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("acc-1", response.getBody().getId());
    }

    @Test
    void createAccount_returnsCreated() {
        CreateAccountRequestDTO request = new CreateAccountRequestDTO();
        request.setUserId("user-1");
        request.setAlias("testalias");

        AccountDTO dto = AccountDTO.builder()
                .id("acc-1")
                .userId("user-1")
                .balance(0L)
                .bitcoinAddress("bc1qabc")
                .alias("testalias@aratiri")
                .fiatEquivalents(Map.of("USD", BigDecimal.ZERO))
                .build();
        when(accountsPort.createAccount(request, "user-1")).thenReturn(dto);

        ResponseEntity<AccountDTO> response = api.createAccount(request, ctx);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("acc-1", response.getBody().getId());
        assertEquals("testalias@aratiri", response.getBody().getAlias());
    }

    @Test
    void getTransactions_returnsTransactions() {
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 1, 31);

        when(accountsPort.getTransactions(from, to, "user-1"))
                .thenReturn(List.of());

        ResponseEntity<AccountTransactionsDTOResponse> response = api.getTransactions(from, to, ctx);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(0, response.getBody().getTransactions().size());
    }
}
