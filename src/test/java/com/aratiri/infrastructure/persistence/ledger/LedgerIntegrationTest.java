package com.aratiri.infrastructure.persistence.ledger;

import com.aratiri.AbstractIntegrationTest;
import com.aratiri.auth.application.dto.RegistrationRequestDTO;
import com.aratiri.auth.application.dto.VerificationRequestDTO;
import com.aratiri.auth.application.port.out.EmailNotificationPort;
import com.aratiri.accounts.application.port.out.CurrencyConversionPort;
import com.aratiri.accounts.application.port.out.LightningAddressPort;
import com.aratiri.infrastructure.persistence.jpa.entity.AccountEntryType;
import com.aratiri.infrastructure.persistence.jpa.entity.TransactionEntity;
import com.aratiri.infrastructure.persistence.jpa.repository.AccountEntryRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.AccountRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.TransactionsRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.VerificationDataRepository;
import com.aratiri.shared.exception.AratiriException;
import com.aratiri.transactions.application.dto.TransactionCurrency;
import com.aratiri.transactions.application.dto.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

class LedgerIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean
    private EmailNotificationPort emailNotificationPort;

    @MockitoBean
    private CurrencyConversionPort currencyConversionPort;

    @MockitoBean
    private LightningAddressPort lightningAddressPort;

    @Autowired
    private VerificationDataRepository verificationDataRepository;

    @Autowired
    private AccountLedgerService accountLedgerService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AccountEntryRepository accountEntryRepository;

    @Autowired
    private TransactionsRepository transactionsRepository;

    private String userId;
    private String accountId;

    @BeforeEach
    void setupUserAndAccount() {
        String email = "ledger-test@example.com";
        String password = "SecurePass123!";
        String name = "Ledger Test";
        String alias = "ledgertest";

        when(currencyConversionPort.getCurrentBtcPrice()).thenReturn(Map.of("usd", BigDecimal.valueOf(50000)));
        when(lightningAddressPort.generateTaprootAddress()).thenReturn("bc1p_test_address");
        doAnswer(invocation -> null).when(emailNotificationPort).sendVerificationEmail(anyString(), anyString());

        webTestClient().post().uri("/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createRegistrationRequest(name, email, password, alias))
                .exchange()
                .expectStatus().isCreated();

        String verificationCode = verificationDataRepository.findById(email)
                .orElseThrow()
                .getCode();

        webTestClient().post().uri("/v1/auth/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createVerificationRequest(email, verificationCode))
                .exchange()
                .expectStatus().isOk();

        var account = accountRepository.findByAlias(alias);
        assertTrue(account.isPresent());
        this.userId = account.get().getUser().getId();
        this.accountId = account.get().getId();
    }

    @Test
    @DisplayName("Balance derived from entries starts at zero")
    void balance_derived_from_entries_starts_at_zero() {
        long balance = accountLedgerService.getCurrentBalanceForAccount(accountId);
        assertEquals(0L, balance);
    }

    @Test
    @DisplayName("Append entry creates immutable ledger record")
    void appendEntry_creates_immutable_record() {
        createTransaction("tx-001", 1000);

        long newBalance = accountLedgerService.appendEntry(accountId, "tx-001", 1000, AccountEntryType.MANUAL_ADJUSTMENT, "Test credit");

        assertEquals(1000L, newBalance);
        assertEquals(1000L, accountLedgerService.getCurrentBalanceForAccount(accountId));

        var entries = accountEntryRepository.findAll();
        assertEquals(1, entries.size());
        assertEquals(1000L, entries.get(0).getDeltaSats());
        assertEquals(1000L, entries.get(0).getBalanceAfter());
        assertEquals(AccountEntryType.MANUAL_ADJUSTMENT, entries.get(0).getEntryType());
        assertEquals("Test credit", entries.get(0).getDescription());
        assertEquals("tx-001", entries.get(0).getTransactionId());
    }

    @Test
    @DisplayName("Multiple entries accumulate balance correctly")
    void multiple_entries_accumulate_balance() {
        createTransaction("tx-001", 1000);
        createTransaction("tx-002", 500);
        createTransaction("tx-003", 300);

        accountLedgerService.appendEntry(accountId, "tx-001", 1000, AccountEntryType.MANUAL_ADJUSTMENT, "First credit");
        accountLedgerService.appendEntry(accountId, "tx-002", 500, AccountEntryType.MANUAL_ADJUSTMENT, "Second credit");
        accountLedgerService.appendEntry(accountId, "tx-003", -300, AccountEntryType.MANUAL_ADJUSTMENT, "Debit");

        long balance = accountLedgerService.getCurrentBalanceForAccount(accountId);
        assertEquals(1200L, balance);

        var entries = accountEntryRepository.findAll();
        assertEquals(3, entries.size());
    }

    @Test
    @DisplayName("Idempotency prevents duplicate entries for same transaction ID")
    void idempotency_prevents_duplicate_entries() {
        createTransaction("tx-duplicate", 1000);

        long firstBalance = accountLedgerService.appendEntry(accountId, "tx-duplicate", 1000, AccountEntryType.MANUAL_ADJUSTMENT, "First attempt");
        long secondBalance = accountLedgerService.appendEntry(accountId, "tx-duplicate", 1000, AccountEntryType.MANUAL_ADJUSTMENT, "Second attempt");

        assertEquals(1000L, firstBalance);
        assertEquals(1000L, secondBalance);

        var entries = accountEntryRepository.findAll();
        assertEquals(1, entries.size());
    }

    @Test
    @DisplayName("Insufficient funds rejected")
    void insufficient_funds_rejected() {
        createTransaction("tx-001", 500);
        createTransaction("tx-002", 1000);

        accountLedgerService.appendEntry(accountId, "tx-001", 500, AccountEntryType.MANUAL_ADJUSTMENT, "Credit");

        AratiriException exception = assertThrows(AratiriException.class, () ->
                accountLedgerService.appendEntry(accountId, "tx-002", -1000, AccountEntryType.MANUAL_ADJUSTMENT, "Overdraft")
        );

        assertTrue(exception.getMessage().contains("Insufficient funds"));

        long balance = accountLedgerService.getCurrentBalanceForAccount(accountId);
        assertEquals(500L, balance);
    }

    @Test
    @DisplayName("Concurrent appends use pessimistic locking to prevent race conditions")
    void concurrent_appends_no_race_condition() throws InterruptedException {
        int threadCount = 10;
        long creditAmount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicReference<Throwable> error = new AtomicReference<>();

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            createTransaction("tx-concurrent-" + index, creditAmount);
            executor.submit(() -> {
                try {
                    accountLedgerService.appendEntry(
                            accountId,
                            "tx-concurrent-" + index,
                            creditAmount,
                            AccountEntryType.MANUAL_ADJUSTMENT,
                            "Concurrent credit " + index
                    );
                } catch (Throwable t) {
                    error.set(t);
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        if (error.get() != null) {
            fail("Concurrent append failed: " + error.get().getMessage());
        }

        long balance = accountLedgerService.getCurrentBalanceForAccount(accountId);
        assertEquals(threadCount * creditAmount, balance);

        var entries = accountEntryRepository.findAll();
        assertEquals(threadCount, entries.size());
    }

    @Test
    @DisplayName("Append entry for user uses userId lookup")
    void appendEntry_for_user_uses_userId_lookup() {
        createTransaction("tx-user-001", 2000);
        TransactionEntity tx = transactionsRepository.findById("tx-user-001").orElseThrow();

        long newBalance = accountLedgerService.appendEntryForUser(tx, 2000, AccountEntryType.MANUAL_ADJUSTMENT, "User credit");

        assertEquals(2000L, newBalance);
        assertEquals(2000L, accountLedgerService.getCurrentBalanceForUser(userId));
    }

    @Test
    @DisplayName("Append entry for non-existent user throws exception")
    void appendEntry_for_non_existent_user_throws() {
        TransactionEntity tx = new TransactionEntity();
        tx.setUserId("non-existent-user");
        tx.setId("tx-001");
        AratiriException exception = assertThrows(AratiriException.class, () ->
                accountLedgerService.appendEntryForUser(tx, 1000, AccountEntryType.MANUAL_ADJUSTMENT, "Test")
        );

        assertTrue(exception.getMessage().contains("Account not found"));
    }

    @Test
    @DisplayName("Append entry for non-existent account throws exception")
    void appendEntry_for_non_existent_account_throws() {
        AratiriException exception = assertThrows(AratiriException.class, () ->
                accountLedgerService.appendEntry("non-existent-account", "tx-001", 1000, AccountEntryType.MANUAL_ADJUSTMENT, "Test")
        );

        assertTrue(exception.getMessage().contains("Account not found"));
    }

    private RegistrationRequestDTO createRegistrationRequest(String name, String email, String password, String alias) {
        RegistrationRequestDTO request = new RegistrationRequestDTO();
        request.setName(name);
        request.setEmail(email);
        request.setPassword(password);
        request.setAlias(alias);
        return request;
    }

    private VerificationRequestDTO createVerificationRequest(String email, String code) {
        VerificationRequestDTO request = new VerificationRequestDTO();
        request.setEmail(email);
        request.setCode(code);
        return request;
    }

    private void createTransaction(String transactionId, long amount) {
        transactionsRepository.save(TransactionEntity.builder()
                .id(transactionId)
                .userId(userId)
                .amount(amount)
                .type(TransactionType.ONCHAIN_CREDIT)
                .currency(TransactionCurrency.BTC)
                .description("Ledger test transaction")
                .referenceId(transactionId)
                .build());
    }
}
