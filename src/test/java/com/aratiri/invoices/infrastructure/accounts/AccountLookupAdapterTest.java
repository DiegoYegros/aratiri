package com.aratiri.invoices.infrastructure.accounts;

import com.aratiri.accounts.application.dto.AccountDTO;
import com.aratiri.accounts.application.port.in.AccountsPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountLookupAdapterTest {

    @Mock
    private AccountsPort accountsPort;

    private AccountLookupAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new AccountLookupAdapter(accountsPort);
    }

    @Test
    void getUserIdByAlias_returnsUserId() {
        AccountDTO dto = AccountDTO.builder()
                .id("acc-1")
                .userId("user-1")
                .balance(0L)
                .build();
        when(accountsPort.getAccountByAlias("testalias")).thenReturn(dto);

        String userId = adapter.getUserIdByAlias("testalias");

        assertEquals("user-1", userId);
    }
}
