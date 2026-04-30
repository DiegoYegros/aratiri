package com.aratiri.payments.infrastructure.account;

import com.aratiri.infrastructure.persistence.jpa.entity.AccountEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.UserEntity;
import com.aratiri.infrastructure.persistence.jpa.repository.AccountRepository;
import com.aratiri.infrastructure.persistence.ledger.AccountLedgerService;
import com.aratiri.payments.domain.PaymentAccount;
import com.aratiri.shared.exception.AratiriException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountRepositoryAdapterTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountLedgerService accountLedgerService;

    private AccountRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new AccountRepositoryAdapter(accountRepository, accountLedgerService);
    }

    @Test
    void getAccount_returnsPaymentAccount() {
        AccountEntity accountEntity = new AccountEntity();
        accountEntity.setId("acc-1");
        accountEntity.setBitcoinAddress("bc1qabc");
        UserEntity user = new UserEntity();
        user.setId("user-1");
        accountEntity.setUser(user);

        when(accountRepository.findByUserId("user-1")).thenReturn(accountEntity);
        when(accountLedgerService.getCurrentBalanceForAccount("acc-1")).thenReturn(50000L);

        PaymentAccount result = adapter.getAccount("user-1");

        assertEquals("user-1", result.userId());
        assertEquals(50000L, result.balance());
        assertEquals("bc1qabc", result.bitcoinAddress());
    }

    @Test
    void getAccount_throwsWhenNotFound() {
        when(accountRepository.findByUserId("unknown")).thenReturn(null);

        assertThrows(AratiriException.class, () -> adapter.getAccount("unknown"));
    }
}
