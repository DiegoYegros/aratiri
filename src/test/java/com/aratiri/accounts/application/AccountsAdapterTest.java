package com.aratiri.accounts.application;

import com.aratiri.accounts.application.dto.*;
import com.aratiri.accounts.application.port.out.*;
import com.aratiri.accounts.domain.Account;
import com.aratiri.accounts.domain.AccountUser;
import com.aratiri.infrastructure.configuration.AratiriProperties;
import com.aratiri.infrastructure.persistence.ledger.AccountLedgerService;
import com.aratiri.infrastructure.persistence.jpa.entity.AccountEntryType;
import com.aratiri.shared.exception.AratiriException;
import com.aratiri.transactions.application.dto.TransactionDTOResponse;
import com.aratiri.transactions.application.dto.TransactionStatus;
import com.aratiri.transactions.application.dto.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountsAdapterTest {

    @Mock
    private AccountPersistencePort accountPersistencePort;

    @Mock
    private LoadUserPort loadUserPort;

    @Mock
    private TransactionsPort transactionsPort;

    @Mock
    private LightningAddressPort lightningAddressPort;

    @Mock
    private CurrencyConversionPort currencyConversionPort;

    @Mock
    private AccountLedgerService accountLedgerService;

    private AratiriProperties properties;
    private AccountsAdapter adapter;

    private static final String USER_ID = "user-123";
    private static final String ACCOUNT_ID = "acc-123";
    private static final String ALIAS = "testalias";
    private static final String BTC_ADDRESS = "bc1qexample";

    @BeforeEach
    void setUp() {
        properties = new AratiriProperties();
        properties.setAratiriBaseUrl("https://aratiri.example.com");
        adapter = new AccountsAdapter(
                accountPersistencePort,
                loadUserPort,
                transactionsPort,
                lightningAddressPort,
                properties,
                currencyConversionPort,
                accountLedgerService
        );
    }

    @Test
    void getAccount_returnsAccountDTO() {
        AccountUser user = new AccountUser(USER_ID);
        Account account = new Account(ACCOUNT_ID, user, 100000L, BTC_ADDRESS, ALIAS);

        when(accountPersistencePort.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(currencyConversionPort.getCurrentBtcPrice()).thenReturn(Map.of("USD", BigDecimal.valueOf(60000)));

        AccountDTO result = adapter.getAccount(ACCOUNT_ID);

        assertNotNull(result);
        assertEquals(ACCOUNT_ID, result.getId());
        assertEquals(100000L, result.getBalance());
        assertEquals(BTC_ADDRESS, result.getBitcoinAddress());
        assertEquals(USER_ID, result.getUserId());
        assertNotNull(result.getAlias());
        assertNotNull(result.getLnurl());
        assertNotNull(result.getFiatEquivalents());
    }

    @Test
    void getAccount_throwsWhenNotFound() {
        when(accountPersistencePort.findById("nonexistent")).thenReturn(Optional.empty());

        AratiriException ex = assertThrows(AratiriException.class, () -> adapter.getAccount("nonexistent"));
        assertEquals(404, ex.getStatus());
    }

    @Test
    void getAccountByUserId_returnsAccountDTO() {
        AccountUser user = new AccountUser(USER_ID);
        Account account = new Account(ACCOUNT_ID, user, 0L, BTC_ADDRESS, ALIAS);

        when(accountPersistencePort.findByUserId(USER_ID)).thenReturn(Optional.of(account));
        when(currencyConversionPort.getCurrentBtcPrice()).thenReturn(Map.of());

        AccountDTO result = adapter.getAccountByUserId(USER_ID);

        assertNotNull(result);
        assertEquals(ACCOUNT_ID, result.getId());
    }

    @Test
    void getAccountByUserId_throwsWhenNotFound() {
        when(accountPersistencePort.findByUserId(USER_ID)).thenReturn(Optional.empty());

        AratiriException ex = assertThrows(AratiriException.class, () -> adapter.getAccountByUserId(USER_ID));
        assertEquals(404, ex.getStatus());
    }

    @Test
    void existsByAlias_delegatesToPersistence() {
        when(accountPersistencePort.existsByAlias(ALIAS)).thenReturn(true);
        assertTrue(adapter.existsByAlias(ALIAS));

        when(accountPersistencePort.existsByAlias("nobody")).thenReturn(false);
        assertFalse(adapter.existsByAlias("nobody"));
    }

    @Test
    void createAccount_success() {
        CreateAccountRequestDTO request = new CreateAccountRequestDTO();
        request.setUserId(USER_ID);
        request.setAlias(ALIAS);

        AccountUser user = new AccountUser(USER_ID);

        when(accountPersistencePort.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(loadUserPort.findById(USER_ID)).thenReturn(Optional.of(user));
        when(lightningAddressPort.generateTaprootAddress()).thenReturn(BTC_ADDRESS);
        when(accountPersistencePort.existsByAlias(ALIAS)).thenReturn(false);
        when(currencyConversionPort.getCurrentBtcPrice()).thenReturn(Map.of());

        Account savedAccount = new Account(ACCOUNT_ID, user, 0L, BTC_ADDRESS, ALIAS);
        when(accountPersistencePort.save(any())).thenReturn(savedAccount);

        AccountDTO result = adapter.createAccount(request, USER_ID);

        assertNotNull(result);
        assertEquals(ACCOUNT_ID, result.getId());
        assertEquals(BTC_ADDRESS, result.getBitcoinAddress());
    }

    @Test
    void createAccount_userIdMismatch_throws() {
        CreateAccountRequestDTO request = new CreateAccountRequestDTO();
        request.setUserId("other-user");

        AratiriException ex = assertThrows(AratiriException.class, () -> adapter.createAccount(request, USER_ID));
        assertTrue(ex.getMessage().contains("does not match"));
    }

    @Test
    void createAccount_existingAccount_throws() {
        CreateAccountRequestDTO request = new CreateAccountRequestDTO();
        request.setUserId(USER_ID);

        AccountUser user = new AccountUser(USER_ID);
        Account existing = new Account(ACCOUNT_ID, user, 0L, BTC_ADDRESS, ALIAS);
        when(accountPersistencePort.findByUserId(USER_ID)).thenReturn(Optional.of(existing));

        AratiriException ex = assertThrows(AratiriException.class, () -> adapter.createAccount(request, USER_ID));
        assertTrue(ex.getMessage().contains("already exists"));
    }

    @Test
    void createAccount_userNotFound_throws() {
        CreateAccountRequestDTO request = new CreateAccountRequestDTO();
        request.setUserId(USER_ID);

        when(accountPersistencePort.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(loadUserPort.findById(USER_ID)).thenReturn(Optional.empty());

        AratiriException ex = assertThrows(AratiriException.class, () -> adapter.createAccount(request, USER_ID));
        assertEquals(404, ex.getStatus());
    }

    @Test
    void createAccount_aliasAlreadyInUse_throws() {
        CreateAccountRequestDTO request = new CreateAccountRequestDTO();
        request.setUserId(USER_ID);
        request.setAlias(ALIAS);

        AccountUser user = new AccountUser(USER_ID);
        when(accountPersistencePort.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(loadUserPort.findById(USER_ID)).thenReturn(Optional.of(user));
        when(lightningAddressPort.generateTaprootAddress()).thenReturn(BTC_ADDRESS);
        when(accountPersistencePort.existsByAlias(ALIAS)).thenReturn(true);

        AratiriException ex = assertThrows(AratiriException.class, () -> adapter.createAccount(request, USER_ID));
        assertTrue(ex.getMessage().contains("already in use"));
    }

    @Test
    void createAccount_autoGeneratesAliasWhenNull() {
        CreateAccountRequestDTO request = new CreateAccountRequestDTO();
        request.setUserId(USER_ID);

        AccountUser user = new AccountUser(USER_ID);

        when(accountPersistencePort.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(loadUserPort.findById(USER_ID)).thenReturn(Optional.of(user));
        when(lightningAddressPort.generateTaprootAddress()).thenReturn(BTC_ADDRESS);
        when(accountPersistencePort.existsByAlias(anyString())).thenReturn(false);
        when(currencyConversionPort.getCurrentBtcPrice()).thenReturn(Map.of());

        Account savedAccount = new Account(ACCOUNT_ID, user, 0L, BTC_ADDRESS, ALIAS);
        when(accountPersistencePort.save(any())).thenReturn(savedAccount);

        AccountDTO result = adapter.createAccount(request, USER_ID);

        assertNotNull(result);
        assertEquals(ACCOUNT_ID, result.getId());
    }

    @Test
    void creditBalance_success() {
        AccountUser user = new AccountUser(USER_ID);
        Account account = new Account(ACCOUNT_ID, user, 50000L, BTC_ADDRESS, ALIAS);

        when(accountPersistencePort.findByUserId(USER_ID)).thenReturn(Optional.of(account));
        when(accountPersistencePort.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(currencyConversionPort.getCurrentBtcPrice()).thenReturn(Map.of());

        AccountDTO result = adapter.creditBalance(USER_ID, 10000L);

        assertNotNull(result);
        verify(accountLedgerService).appendEntry(
                ACCOUNT_ID, null, 10000L, AccountEntryType.MANUAL_ADJUSTMENT, "Manual credit adjustment");
    }

    @Test
    void creditBalance_accountNotFound() {
        when(accountPersistencePort.findByUserId(USER_ID)).thenReturn(Optional.empty());

        AratiriException ex = assertThrows(AratiriException.class, () -> adapter.creditBalance(USER_ID, 10000L));
        assertEquals(404, ex.getStatus());
    }

    @Test
    void getAccountByAlias_success() {
        AccountUser user = new AccountUser(USER_ID);
        Account account = new Account(ACCOUNT_ID, user, 0L, BTC_ADDRESS, ALIAS);

        when(accountPersistencePort.findByAlias(ALIAS)).thenReturn(Optional.of(account));
        when(currencyConversionPort.getCurrentBtcPrice()).thenReturn(Map.of());

        AccountDTO result = adapter.getAccountByAlias(ALIAS);

        assertNotNull(result);
        assertEquals(ACCOUNT_ID, result.getId());
    }

    @Test
    void getAccountByAlias_notFound() {
        when(accountPersistencePort.findByAlias("nobody")).thenReturn(Optional.empty());

        AratiriException ex = assertThrows(AratiriException.class, () -> adapter.getAccountByAlias("nobody"));
        assertEquals(404, ex.getStatus());
    }

    @Test
    void getTransactions_filtersFailedAndMapsToDTO() {
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 1, 31);

        TransactionDTOResponse txn = TransactionDTOResponse.builder()
                .id("tx-1")
                .createdAt(OffsetDateTime.now())
                .amountSat(100000L)
                .type(TransactionType.LIGHTNING_DEBIT)
                .status(TransactionStatus.COMPLETED)
                .build();

        when(transactionsPort.getTransactions(any(), any(), eq(USER_ID))).thenReturn(List.of(txn));
        when(currencyConversionPort.getCurrentBtcPrice()).thenReturn(Map.of("USD", BigDecimal.valueOf(60000)));

        List<AccountTransactionDTO> result = adapter.getTransactions(from, to, USER_ID);

        assertEquals(1, result.size());
        assertEquals("tx-1", result.getFirst().getId());
        assertEquals(100000L, result.getFirst().getAmount());
        assertEquals(AccountTransactionType.DEBIT, result.getFirst().getType());
        assertEquals(AccountTransactionStatus.COMPLETED, result.getFirst().getStatus());
    }

    @Test
    void getTransactions_returnsCreditType() {
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 1, 31);

        TransactionDTOResponse txn = TransactionDTOResponse.builder()
                .id("tx-2")
                .createdAt(OffsetDateTime.now())
                .amountSat(50000L)
                .type(TransactionType.ONCHAIN_CREDIT)
                .status(TransactionStatus.COMPLETED)
                .build();

        when(transactionsPort.getTransactions(any(), any(), eq(USER_ID))).thenReturn(List.of(txn));
        when(currencyConversionPort.getCurrentBtcPrice()).thenReturn(Map.of());

        List<AccountTransactionDTO> result = adapter.getTransactions(from, to, USER_ID);

        assertEquals(1, result.size());
        assertEquals(AccountTransactionType.CREDIT, result.getFirst().getType());
    }

    @Test
    void getTransactions_filtersOutFailed() {
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 1, 31);

        TransactionDTOResponse failed = TransactionDTOResponse.builder()
                .id("tx-failed")
                .createdAt(OffsetDateTime.now())
                .amountSat(1000L)
                .type(TransactionType.LIGHTNING_DEBIT)
                .status(TransactionStatus.FAILED)
                .build();

        when(transactionsPort.getTransactions(any(), any(), eq(USER_ID))).thenReturn(List.of(failed));
        when(currencyConversionPort.getCurrentBtcPrice()).thenReturn(Map.of());

        List<AccountTransactionDTO> result = adapter.getTransactions(from, to, USER_ID);

        assertTrue(result.isEmpty());
    }
}
