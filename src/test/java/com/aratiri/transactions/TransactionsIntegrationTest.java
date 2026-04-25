package com.aratiri.transactions;

import com.aratiri.AbstractIntegrationTest;
import com.aratiri.auth.application.dto.RegistrationRequestDTO;
import com.aratiri.auth.application.dto.VerificationRequestDTO;
import com.aratiri.auth.application.port.out.EmailNotificationPort;
import com.aratiri.accounts.application.port.out.CurrencyConversionPort;
import com.aratiri.accounts.application.port.out.LightningAddressPort;
import com.aratiri.infrastructure.persistence.jpa.entity.AccountEntryEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.TransactionEventEntity;
import com.aratiri.infrastructure.persistence.jpa.repository.AccountEntryRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.AccountRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.TransactionEventRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.VerificationDataRepository;
import com.aratiri.shared.exception.AratiriException;
import com.aratiri.transactions.application.dto.CreateTransactionRequest;
import com.aratiri.transactions.application.dto.TransactionCurrency;
import com.aratiri.transactions.application.dto.TransactionDTOResponse;
import com.aratiri.transactions.application.dto.TransactionStatus;
import com.aratiri.transactions.application.dto.TransactionType;
import com.aratiri.transactions.application.port.in.TransactionsPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

class TransactionsIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean
    private EmailNotificationPort emailNotificationPort;

    @MockitoBean
    private CurrencyConversionPort currencyConversionPort;

    @MockitoBean
    private LightningAddressPort lightningAddressPort;

    @Autowired
    private VerificationDataRepository verificationDataRepository;

    @Autowired
    private TransactionsPort transactionsPort;

    @Autowired
    private TransactionEventRepository transactionEventRepository;

    @Autowired
    private AccountEntryRepository accountEntryRepository;

    @Autowired
    private AccountRepository accountRepository;

    private String userId;

    @BeforeEach
    void setupUserAndAccount() {
        String email = "tx-test@example.com";
        String password = "SecurePass123!";
        String name = "Transaction Test";
        String alias = "txtest";

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
    }

    @Test
    @DisplayName("Create transaction creates pending status event")
    void createTransaction_creates_pending_status_event() {
        CreateTransactionRequest request = CreateTransactionRequest.builder()
                .userId(userId)
                .amountSat(1000)
                .currency(TransactionCurrency.BTC)
                .type(TransactionType.ONCHAIN_CREDIT)
                .status(TransactionStatus.PENDING)
                .description("Test deposit")
                .referenceId("ref-001")
                .build();

        TransactionDTOResponse response = transactionsPort.createTransaction(request);

        assertNotNull(response.getId());
        assertEquals(TransactionStatus.PENDING, response.getStatus());
        assertEquals(1000L, response.getAmountSat());
        assertEquals("ref-001", response.getReferenceId());

        List<TransactionEventEntity> events = transactionEventRepository.findByTransaction_IdOrderByCreatedAtAsc(response.getId());
        assertEquals(1, events.size());
        assertEquals(TransactionStatus.PENDING, events.get(0).getStatus());
    }

    @Test
    @DisplayName("Confirm transaction creates ledger entries for credit")
    void confirmTransaction_creates_ledger_entries_for_credit() {
        CreateTransactionRequest request = CreateTransactionRequest.builder()
                .userId(userId)
                .amountSat(5000)
                .currency(TransactionCurrency.BTC)
                .type(TransactionType.ONCHAIN_CREDIT)
                .status(TransactionStatus.PENDING)
                .description("Test deposit")
                .referenceId("ref-002")
                .build();

        TransactionDTOResponse created = transactionsPort.createTransaction(request);

        TransactionDTOResponse confirmed = transactionsPort.confirmTransaction(created.getId(), userId);

        assertEquals(TransactionStatus.COMPLETED, confirmed.getStatus());
        assertNotNull(confirmed.getBalanceAfterSat());
        assertEquals(5000L, confirmed.getBalanceAfterSat());

        List<AccountEntryEntity> entries = accountEntryRepository.findAll();
        assertEquals(1, entries.size());
        assertEquals(5000L, entries.get(0).getDeltaSats());
        assertEquals(5000L, entries.get(0).getBalanceAfter());
    }

    @Test
    @DisplayName("Confirm transaction rejects non-pending transaction")
    void confirmTransaction_rejects_non_pending() {
        CreateTransactionRequest request = CreateTransactionRequest.builder()
                .userId(userId)
                .amountSat(1000)
                .currency(TransactionCurrency.BTC)
                .type(TransactionType.ONCHAIN_CREDIT)
                .status(TransactionStatus.PENDING)
                .description("Test deposit")
                .referenceId("ref-003")
                .build();

        TransactionDTOResponse created = transactionsPort.createTransaction(request);
        transactionsPort.confirmTransaction(created.getId(), userId);
        String transactionId = created.getId();

        AratiriException exception = assertThrows(AratiriException.class, () ->
                transactionsPort.confirmTransaction(transactionId, userId)
        );

        assertTrue(exception.getMessage().contains("not valid for confirmation"));
    }

    @Test
    @DisplayName("Fail transaction does not create ledger entries")
    void failTransaction_no_ledger_impact() {
        CreateTransactionRequest request = CreateTransactionRequest.builder()
                .userId(userId)
                .amountSat(1000)
                .currency(TransactionCurrency.BTC)
                .type(TransactionType.ONCHAIN_CREDIT)
                .status(TransactionStatus.PENDING)
                .description("Test deposit")
                .referenceId("ref-004")
                .build();

        TransactionDTOResponse created = transactionsPort.createTransaction(request);

        transactionsPort.failTransaction(created.getId(), "Test failure reason");

        List<TransactionEventEntity> events = transactionEventRepository.findByTransaction_IdOrderByCreatedAtAsc(created.getId());
        assertEquals(2, events.size());
        assertEquals(TransactionStatus.PENDING, events.get(0).getStatus());
        assertEquals(TransactionStatus.FAILED, events.get(1).getStatus());

        long entryCount = accountEntryRepository.count();
        assertEquals(0, entryCount);
    }

    @Test
    @DisplayName("Create and settle transaction is atomic")
    void createAndSettleTransaction_atomic() {
        CreateTransactionRequest request = CreateTransactionRequest.builder()
                .userId(userId)
                .amountSat(3000)
                .currency(TransactionCurrency.BTC)
                .type(TransactionType.ONCHAIN_CREDIT)
                .description("Atomic settlement")
                .referenceId("ref-005")
                .build();

        TransactionDTOResponse response = transactionsPort.createAndSettleTransaction(request);

        assertEquals(TransactionStatus.COMPLETED, response.getStatus());
        assertEquals(3000L, response.getBalanceAfterSat());

        List<TransactionEventEntity> events = transactionEventRepository.findByTransaction_IdOrderByCreatedAtAsc(response.getId());
        assertEquals(2, events.size());
        assertEquals(TransactionStatus.PENDING, events.get(0).getStatus());
        assertEquals(TransactionStatus.COMPLETED, events.get(1).getStatus());

        List<AccountEntryEntity> entries = accountEntryRepository.findAll();
        assertEquals(1, entries.size());
        assertEquals(3000L, entries.get(0).getDeltaSats());
    }

    @Test
    @DisplayName("Create and settle rejects non-settleable types")
    void createAndSettleTransaction_rejects_non_settleable() {
        CreateTransactionRequest request = CreateTransactionRequest.builder()
                .userId(userId)
                .amountSat(1000)
                .currency(TransactionCurrency.BTC)
                .type(TransactionType.ONCHAIN_DEBIT)
                .description("Should fail")
                .referenceId("ref-006")
                .build();

        AratiriException exception = assertThrows(AratiriException.class, () ->
                transactionsPort.createAndSettleTransaction(request)
        );

        assertTrue(exception.getMessage().contains("not valid for the create-and-settle flow"));
    }

    @Test
    @DisplayName("Reference ID uniqueness per user is enforced")
    void referenceId_unique_per_user() {
        CreateTransactionRequest request = CreateTransactionRequest.builder()
                .userId(userId)
                .amountSat(1000)
                .currency(TransactionCurrency.BTC)
                .type(TransactionType.ONCHAIN_CREDIT)
                .description("First")
                .referenceId("unique-ref")
                .build();

        transactionsPort.createTransaction(request);

        assertTrue(transactionsPort.existsByReferenceId("unique-ref"));
    }

    @Test
    @DisplayName("Get transactions returns filtered list")
    void getTransactions_returns_filtered_list() {
        CreateTransactionRequest request = CreateTransactionRequest.builder()
                .userId(userId)
                .amountSat(2000)
                .currency(TransactionCurrency.BTC)
                .type(TransactionType.ONCHAIN_CREDIT)
                .status(TransactionStatus.PENDING)
                .description("Test deposit")
                .referenceId("ref-007")
                .build();

        transactionsPort.createTransaction(request);

        java.time.Instant now = java.time.Instant.now();
        java.time.Instant from = now.minusSeconds(3600);
        java.time.Instant to = now.plusSeconds(3600);

        List<TransactionDTOResponse> transactions = transactionsPort.getTransactions(from, to, userId);

        assertFalse(transactions.isEmpty());
        assertTrue(transactions.stream().anyMatch(t -> "ref-007".equals(t.getReferenceId())));
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
}
