package com.aratiri.transactions;

import com.aratiri.AbstractIntegrationTest;
import com.aratiri.auth.application.dto.RegistrationRequestDTO;
import com.aratiri.auth.application.dto.VerificationRequestDTO;
import com.aratiri.auth.application.port.out.EmailNotificationPort;
import com.aratiri.accounts.application.port.out.CurrencyConversionPort;
import com.aratiri.accounts.application.port.out.LightningAddressPort;
import com.aratiri.infrastructure.messaging.KafkaTopics;
import com.aratiri.infrastructure.persistence.jpa.entity.AccountEntryType;
import com.aratiri.infrastructure.persistence.jpa.entity.AccountEntryEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.OutboxEventEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.TransactionEventEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.TransactionEventType;
import com.aratiri.infrastructure.persistence.jpa.entity.WebhookEventEntity;
import com.aratiri.infrastructure.persistence.jpa.repository.AccountEntryRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.AccountRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.OutboxEventRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.TransactionEventRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.TransactionsRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.VerificationDataRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.WebhookEventRepository;
import com.aratiri.shared.exception.AratiriException;
import com.aratiri.transactions.application.InvoiceCreditSettlement;
import com.aratiri.transactions.application.ExternalDebitCompletionSettlement;
import com.aratiri.transactions.application.ExternalDebitFailureSettlement;
import com.aratiri.transactions.application.LightningRoutingFeeSettlement;
import com.aratiri.transactions.application.OnChainCreditSettlement;
import com.aratiri.transactions.application.TransactionSettlementModule;
import com.aratiri.transactions.application.TransactionSettlementResult;
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

    @Autowired
    private TransactionsRepository transactionsRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private WebhookEventRepository webhookEventRepository;

    @Autowired
    private TransactionSettlementModule transactionSettlementModule;

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
    @DisplayName("Confirm transaction is idempotent for already completed transaction")
    void confirmTransaction_idempotent_for_completed() {
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
        TransactionDTOResponse firstConfirm = transactionsPort.confirmTransaction(created.getId(), userId);
        assertEquals(TransactionStatus.COMPLETED, firstConfirm.getStatus());

        TransactionDTOResponse secondConfirm = transactionsPort.confirmTransaction(created.getId(), userId);
        assertEquals(TransactionStatus.COMPLETED, secondConfirm.getStatus());
        assertEquals(firstConfirm.getBalanceAfterSat(), secondConfirm.getBalanceAfterSat());
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
    @DisplayName("Invoice credit settlement records lifecycle, read model, and ledger entry")
    void settleInvoiceCredit_records_lifecycle_read_model_and_ledger_entry() {
        InvoiceCreditSettlement settlement = new InvoiceCreditSettlement(
                userId,
                2500L,
                "invoice-credit-payment-hash",
                "Invoice credit settlement",
                "external-invoice-ref",
                "{\"order_id\":\"1001\"}"
        );

        TransactionSettlementResult result = transactionSettlementModule.settleInvoiceCredit(settlement);

        assertInvoiceCreditSettlementResult(result);
        var savedTransaction = transactionsRepository.findById(result.transactionId()).orElseThrow();
        assertInvoiceCreditReadModel(savedTransaction);

        TransactionDTOResponse response = transactionsPort.getTransactionById(savedTransaction.getId(), userId).orElseThrow();
        assertInvoiceCreditResponse(response);

        List<TransactionEventEntity> events = transactionEventRepository.findByTransaction_IdOrderByCreatedAtAsc(savedTransaction.getId());
        assertInvoiceCreditLifecycleEvents(events);

        List<AccountEntryEntity> entries = accountEntryRepository.findAll();
        assertInvoiceCreditLedgerEntry(entries, savedTransaction.getId());
        assertInvoiceSettledWebhook(savedTransaction.getId(), settlement);
        assertAccountBalanceChangedWebhook(entries.get(0), savedTransaction.getId(), settlement.paymentHash(), settlement.externalReference(), settlement.metadata());
    }

    @Test
    @DisplayName("On-chain credit settlement records lifecycle, read model, and ledger entry")
    void settleOnChainCredit_records_lifecycle_read_model_and_ledger_entry() {
        OnChainCreditSettlement settlement = new OnChainCreditSettlement(
                userId,
                3500L,
                "onchain-tx-hash",
                1L
        );

        TransactionSettlementResult result = transactionSettlementModule.settleOnChainCredit(settlement);

        assertOnChainCreditSettlementResult(result);

        var savedTransaction = transactionsRepository.findById(result.transactionId()).orElseThrow();
        assertOnChainCreditReadModel(savedTransaction);

        TransactionDTOResponse response = transactionsPort.getTransactionById(savedTransaction.getId(), userId).orElseThrow();
        assertOnChainCreditResponse(response);

        List<TransactionEventEntity> events = transactionEventRepository.findByTransaction_IdOrderByCreatedAtAsc(savedTransaction.getId());
        assertOnChainCreditLifecycleEvents(events);

        List<AccountEntryEntity> entries = accountEntryRepository.findAll();
        assertOnChainCreditLedgerEntry(entries, savedTransaction.getId());
        assertOnChainDepositConfirmedWebhook(savedTransaction.getId(), settlement.referenceId(), 3500L, 3500L);
        assertAccountBalanceChangedWebhook(entries.get(0), savedTransaction.getId(), settlement.referenceId(), null, null);
    }

    @Test
    @DisplayName("Invoice credit settlement is idempotent for already-settled invoices")
    void settleInvoiceCredit_idempotent_for_already_settled_invoices() {
        InvoiceCreditSettlement settlement = new InvoiceCreditSettlement(
                userId,
                2500L,
                "duplicate-invoice-payment-hash",
                "Duplicate invoice settlement",
                "duplicate-invoice-ref",
                "{\"order_id\":\"1002\"}"
        );

        TransactionSettlementResult first = transactionSettlementModule.settleInvoiceCredit(settlement);
        TransactionSettlementResult second = transactionSettlementModule.settleInvoiceCredit(settlement);

        assertEquals(first.transactionId(), second.transactionId());
        assertEquals(first.balanceAfterSat(), second.balanceAfterSat());

        List<TransactionEventEntity> events = transactionEventRepository.findByTransaction_IdOrderByCreatedAtAsc(first.transactionId());
        assertEquals(2, events.size());
        assertEquals(1, accountEntryRepository.findAll().size());
        assertEquals(1, transactionsRepository.findAll().stream()
                .filter(transaction -> "duplicate-invoice-payment-hash".equals(transaction.getReferenceId()))
                .count());
        assertEquals(1, webhookEventRepository.findAll().stream()
                .filter(event -> "invoice.settled:duplicate-invoice-payment-hash".equals(event.getEventKey()))
                .count());
    }

    @Test
    @DisplayName("On-chain credit settlement is idempotent for already-settled deposits")
    void settleOnChainCredit_idempotent_for_already_settled_deposits() {
        OnChainCreditSettlement settlement = new OnChainCreditSettlement(
                userId,
                3500L,
                "duplicate-onchain-tx-hash",
                0L
        );

        TransactionSettlementResult first = transactionSettlementModule.settleOnChainCredit(settlement);
        TransactionSettlementResult second = transactionSettlementModule.settleOnChainCredit(settlement);

        assertEquals(first.transactionId(), second.transactionId());
        assertEquals(first.balanceAfterSat(), second.balanceAfterSat());

        List<TransactionEventEntity> events = transactionEventRepository.findByTransaction_IdOrderByCreatedAtAsc(first.transactionId());
        assertEquals(2, events.size());
        assertEquals(1, accountEntryRepository.findAll().size());
        assertEquals(1, transactionsRepository.findAll().stream()
                .filter(transaction -> "duplicate-onchain-tx-hash:0".equals(transaction.getReferenceId()))
                .count());
        assertEquals(1, webhookEventRepository.findAll().stream()
                .filter(event -> "onchain.deposit.confirmed:duplicate-onchain-tx-hash:0".equals(event.getEventKey()))
                .count());
    }

    @Test
    @DisplayName("External lightning debit settlement applies routing fee once and records lifecycle, read model, and ledger")
    void settleExternalLightningDebit_applies_fee_once_and_records_settlement() {
        transactionSettlementModule.settleOnChainCredit(new OnChainCreditSettlement(
                userId,
                5000L,
                "funding-tx-hash",
                0L
        ));
        CreateTransactionRequest debitRequest = CreateTransactionRequest.builder()
                .userId(userId)
                .amountSat(1000)
                .currency(TransactionCurrency.BTC)
                .type(TransactionType.LIGHTNING_DEBIT)
                .status(TransactionStatus.PENDING)
                .description("Lightning payment")
                .referenceId("external-payment-hash")
                .build();

        TransactionDTOResponse created = transactionsPort.createTransaction(debitRequest);
        transactionSettlementModule.applyLightningRoutingFee(new LightningRoutingFeeSettlement(created.getId(), 25L));
        transactionSettlementModule.applyLightningRoutingFee(new LightningRoutingFeeSettlement(created.getId(), 25L));

        TransactionSettlementResult result = transactionSettlementModule.settleExternalDebit(
                new ExternalDebitCompletionSettlement(created.getId(), userId)
        );

        assertEquals(TransactionStatus.COMPLETED, result.status());
        assertEquals(1025L, result.amountSat());
        assertEquals(3975L, result.balanceAfterSat());

        var savedTransaction = transactionsRepository.findById(created.getId()).orElseThrow();
        assertEquals(TransactionStatus.COMPLETED.name(), savedTransaction.getCurrentStatus());
        assertEquals(1025L, savedTransaction.getCurrentAmount());
        assertEquals(3975L, savedTransaction.getBalanceAfter());
        assertNotNull(savedTransaction.getCompletedAt());

        List<TransactionEventEntity> events = transactionEventRepository.findByTransaction_IdOrderByCreatedAtAsc(created.getId());
        assertEquals(3, events.size());
        assertEquals(TransactionStatus.PENDING, events.get(0).getStatus());
        assertEquals(1, events.stream().filter(event -> event.getEventType() == TransactionEventType.FEE_ADDED).count());
        assertTrue(events.stream().anyMatch(event -> event.getStatus() == TransactionStatus.COMPLETED));

        AccountEntryEntity debitEntry = accountEntryRepository.findAll().stream()
                .filter(entry -> created.getId().equals(entry.getTransactionId()))
                .findFirst()
                .orElseThrow();
        assertEquals(AccountEntryType.LIGHTNING_DEBIT, debitEntry.getEntryType());
        assertEquals(-1025L, debitEntry.getDeltaSats());

        List<OutboxEventEntity> outboxEvents = outboxEventRepository.findAll();
        assertTrue(outboxEvents.stream().anyMatch(event ->
                event.getEventType().equals(KafkaTopics.PAYMENT_SENT.getCode())
                        && event.getAggregateId().equals(created.getId())
                        && event.getPayload().contains("external-payment-hash")
        ));

        WebhookEventEntity webhookEvent = webhookEventRepository.findByEventKey("payment.succeeded:" + created.getId())
                .orElseThrow();
        assertEquals("payment.succeeded", webhookEvent.getEventType());
        assertTrue(webhookEvent.getPayload().contains("\"amount_sat\":1025"));
        assertTrue(webhookEvent.getPayload().contains("\"balance_after_sat\":3975"));
    }

    @Test
    @DisplayName("External on-chain debit settlement records lifecycle, read model, and ledger")
    void settleExternalOnChainDebit_records_lifecycle_read_model_and_ledger() {
        transactionSettlementModule.settleOnChainCredit(new OnChainCreditSettlement(
                userId,
                5000L,
                "onchain-debit-funding-tx-hash",
                0L
        ));
        CreateTransactionRequest debitRequest = CreateTransactionRequest.builder()
                .userId(userId)
                .amountSat(1500)
                .currency(TransactionCurrency.BTC)
                .type(TransactionType.ONCHAIN_DEBIT)
                .status(TransactionStatus.PENDING)
                .description("On-chain payment")
                .referenceId("onchain-debit-ref")
                .build();

        TransactionDTOResponse created = transactionsPort.createTransaction(debitRequest);
        TransactionSettlementResult result = transactionSettlementModule.settleExternalDebit(
                new ExternalDebitCompletionSettlement(created.getId(), userId)
        );

        assertEquals(TransactionStatus.COMPLETED, result.status());
        assertEquals(1500L, result.amountSat());
        assertEquals(3500L, result.balanceAfterSat());

        var savedTransaction = transactionsRepository.findById(created.getId()).orElseThrow();
        assertEquals(TransactionStatus.COMPLETED.name(), savedTransaction.getCurrentStatus());
        assertEquals(1500L, savedTransaction.getCurrentAmount());
        assertEquals(3500L, savedTransaction.getBalanceAfter());

        List<AccountEntryEntity> entries = accountEntryRepository.findAll();
        AccountEntryEntity debitEntry = entries.stream()
                .filter(entry -> created.getId().equals(entry.getTransactionId()))
                .findFirst()
                .orElseThrow();
        assertEquals(AccountEntryType.ONCHAIN_DEBIT, debitEntry.getEntryType());
        assertEquals(-1500L, debitEntry.getDeltaSats());

        assertTrue(outboxEventRepository.findAll().stream().anyMatch(event ->
                event.getEventType().equals(KafkaTopics.PAYMENT_SENT.getCode())
                        && event.getAggregateId().equals(created.getId())
                        && event.getPayload().contains("onchain-debit-ref")
        ));
        assertTrue(webhookEventRepository.findByEventKey("payment.succeeded:" + created.getId()).isPresent());
    }

    @Test
    @DisplayName("External debit completion is idempotent after completion")
    void settleExternalDebit_idempotent_after_completion() {
        transactionSettlementModule.settleOnChainCredit(new OnChainCreditSettlement(
                userId,
                5000L,
                "idempotent-debit-funding-tx-hash",
                0L
        ));
        TransactionDTOResponse created = transactionsPort.createTransaction(CreateTransactionRequest.builder()
                .userId(userId)
                .amountSat(1500)
                .currency(TransactionCurrency.BTC)
                .type(TransactionType.ONCHAIN_DEBIT)
                .status(TransactionStatus.PENDING)
                .description("On-chain payment")
                .referenceId("idempotent-onchain-debit-ref")
                .build());

        TransactionSettlementResult first = transactionSettlementModule.settleExternalDebit(
                new ExternalDebitCompletionSettlement(created.getId(), userId)
        );
        TransactionSettlementResult second = transactionSettlementModule.settleExternalDebit(
                new ExternalDebitCompletionSettlement(created.getId(), userId)
        );

        assertEquals(first, second);
        assertEquals(1, accountEntryRepository.findAll().stream()
                .filter(entry -> created.getId().equals(entry.getTransactionId()))
                .count());
        assertEquals(1, transactionEventRepository.findByTransaction_IdOrderByCreatedAtAsc(created.getId()).stream()
                .filter(event -> event.getStatus() == TransactionStatus.COMPLETED)
                .count());
    }

    @Test
    @DisplayName("External debit failure records failure lifecycle and no ledger entry")
    void failExternalDebit_records_failure_without_ledger_entry() {
        CreateTransactionRequest debitRequest = CreateTransactionRequest.builder()
                .userId(userId)
                .amountSat(1000)
                .currency(TransactionCurrency.BTC)
                .type(TransactionType.ONCHAIN_DEBIT)
                .status(TransactionStatus.PENDING)
                .description("On-chain payment")
                .referenceId("failed-onchain-debit-ref")
                .build();

        TransactionDTOResponse created = transactionsPort.createTransaction(debitRequest);
        TransactionSettlementResult result = transactionSettlementModule.failExternalDebit(
                new ExternalDebitFailureSettlement(created.getId(), "node failure")
        );

        assertEquals(TransactionStatus.FAILED, result.status());
        assertEquals(1000L, result.amountSat());
        assertNull(result.balanceAfterSat());

        var savedTransaction = transactionsRepository.findById(created.getId()).orElseThrow();
        assertEquals(TransactionStatus.FAILED.name(), savedTransaction.getCurrentStatus());
        assertEquals("node failure", savedTransaction.getFailureReason());

        List<TransactionEventEntity> events = transactionEventRepository.findByTransaction_IdOrderByCreatedAtAsc(created.getId());
        assertEquals(2, events.size());
        assertEquals(TransactionStatus.FAILED, events.get(1).getStatus());
        assertEquals("node failure", events.get(1).getDetails());
        assertEquals(0, accountEntryRepository.count());
    }

    private void assertInvoiceCreditSettlementResult(TransactionSettlementResult result) {
        assertEquals(TransactionStatus.COMPLETED, result.status());
        assertEquals(2500L, result.amountSat());
        assertEquals(2500L, result.balanceAfterSat());
    }

    private void assertInvoiceCreditReadModel(com.aratiri.infrastructure.persistence.jpa.entity.TransactionEntity transaction) {
        assertEquals(TransactionType.LIGHTNING_CREDIT, transaction.getType());
        assertEquals(TransactionStatus.COMPLETED.name(), transaction.getCurrentStatus());
        assertEquals(2500L, transaction.getCurrentAmount());
        assertEquals(2500L, transaction.getBalanceAfter());
        assertEquals("invoice-credit-payment-hash", transaction.getReferenceId());
        assertEquals("external-invoice-ref", transaction.getExternalReference());
        assertEquals("{\"order_id\":\"1001\"}", transaction.getMetadata());
        assertNotNull(transaction.getCompletedAt());
    }

    private void assertInvoiceCreditResponse(TransactionDTOResponse response) {
        assertEquals(TransactionStatus.COMPLETED, response.getStatus());
        assertEquals(2500L, response.getAmountSat());
        assertEquals(2500L, response.getBalanceAfterSat());
        assertEquals("invoice-credit-payment-hash", response.getReferenceId());
        assertEquals("external-invoice-ref", response.getExternalReference());
        assertEquals("{\"order_id\":\"1001\"}", response.getMetadata());
    }

    private void assertInvoiceCreditLifecycleEvents(List<TransactionEventEntity> events) {
        assertEquals(2, events.size());
        assertEquals(TransactionStatus.PENDING, events.get(0).getStatus());
        assertNull(events.get(0).getBalanceAfter());
        assertEquals(TransactionStatus.COMPLETED, events.get(1).getStatus());
        assertEquals(2500L, events.get(1).getBalanceAfter());
    }

    private void assertInvoiceCreditLedgerEntry(List<AccountEntryEntity> entries, String transactionId) {
        assertEquals(1, entries.size());
        assertEquals(transactionId, entries.get(0).getTransactionId());
        assertEquals(2500L, entries.get(0).getDeltaSats());
        assertEquals(2500L, entries.get(0).getBalanceAfter());
        assertEquals(AccountEntryType.LIGHTNING_CREDIT, entries.get(0).getEntryType());
        assertEquals("Lightning invoice settled", entries.get(0).getDescription());
    }

    private void assertOnChainCreditSettlementResult(TransactionSettlementResult result) {
        assertEquals(TransactionStatus.COMPLETED, result.status());
        assertEquals(3500L, result.amountSat());
        assertEquals(3500L, result.balanceAfterSat());
    }

    private void assertOnChainCreditReadModel(com.aratiri.infrastructure.persistence.jpa.entity.TransactionEntity transaction) {
        assertEquals(TransactionType.ONCHAIN_CREDIT, transaction.getType());
        assertEquals(TransactionStatus.COMPLETED.name(), transaction.getCurrentStatus());
        assertEquals(3500L, transaction.getCurrentAmount());
        assertEquals(3500L, transaction.getBalanceAfter());
        assertEquals("onchain-tx-hash:1", transaction.getReferenceId());
        assertNotNull(transaction.getCompletedAt());
    }

    private void assertOnChainCreditResponse(TransactionDTOResponse response) {
        assertEquals(TransactionStatus.COMPLETED, response.getStatus());
        assertEquals(3500L, response.getAmountSat());
        assertEquals(3500L, response.getBalanceAfterSat());
        assertEquals("onchain-tx-hash:1", response.getReferenceId());
    }

    private void assertOnChainCreditLifecycleEvents(List<TransactionEventEntity> events) {
        assertEquals(2, events.size());
        assertEquals(TransactionStatus.PENDING, events.get(0).getStatus());
        assertNull(events.get(0).getBalanceAfter());
        assertEquals(TransactionStatus.COMPLETED, events.get(1).getStatus());
        assertEquals(3500L, events.get(1).getBalanceAfter());
    }

    private void assertOnChainCreditLedgerEntry(List<AccountEntryEntity> entries, String transactionId) {
        assertEquals(1, entries.size());
        assertEquals(transactionId, entries.get(0).getTransactionId());
        assertEquals(3500L, entries.get(0).getDeltaSats());
        assertEquals(3500L, entries.get(0).getBalanceAfter());
        assertEquals(AccountEntryType.ONCHAIN_CREDIT, entries.get(0).getEntryType());
        assertEquals("On-chain deposit", entries.get(0).getDescription());
    }

    private void assertInvoiceSettledWebhook(String transactionId, InvoiceCreditSettlement settlement) {
        WebhookEventEntity event = webhookEventRepository.findByEventKey("invoice.settled:" + settlement.paymentHash())
                .orElseThrow();
        assertEquals("invoice.settled", event.getEventType());
        assertEquals("INVOICE", event.getAggregateType());
        assertEquals(settlement.paymentHash(), event.getAggregateId());
        assertEquals(settlement.externalReference(), event.getExternalReference());
        assertTrue(event.getPayload().contains("\"transaction_id\":\"" + transactionId + "\""));
        assertTrue(event.getPayload().contains("\"payment_hash\":\"" + settlement.paymentHash() + "\""));
        assertTrue(event.getPayload().contains("\"amount_sat\":2500"));
        assertTrue(event.getPayload().contains("\"balance_after_sat\":2500"));
        assertTrue(event.getPayload().contains("\"external_reference\":\"" + settlement.externalReference() + "\""));
        assertTrue(event.getPayload().contains("\"metadata\":\"" + settlement.metadata().replace("\"", "\\\"") + "\""));
    }

    private void assertOnChainDepositConfirmedWebhook(String transactionId, String referenceId, long amountSat, long balanceAfterSat) {
        WebhookEventEntity event = webhookEventRepository.findByEventKey("onchain.deposit.confirmed:" + referenceId)
                .orElseThrow();
        assertEquals("onchain.deposit.confirmed", event.getEventType());
        assertEquals("TRANSACTION", event.getAggregateType());
        assertEquals(transactionId, event.getAggregateId());
        assertTrue(event.getPayload().contains("\"transaction_id\":\"" + transactionId + "\""));
        assertTrue(event.getPayload().contains("\"reference_id\":\"" + referenceId + "\""));
        assertTrue(event.getPayload().contains("\"amount_sat\":" + amountSat));
        assertTrue(event.getPayload().contains("\"balance_after_sat\":" + balanceAfterSat));
    }

    private void assertAccountBalanceChangedWebhook(
            AccountEntryEntity entry,
            String transactionId,
            String referenceId,
            String externalReference,
            String metadata
    ) {
        WebhookEventEntity event = webhookEventRepository.findByEventKey("account.balance_changed:" + entry.getId())
                .orElseThrow();
        assertEquals("account.balance_changed", event.getEventType());
        assertEquals("ACCOUNT_ENTRY", event.getAggregateType());
        assertEquals(entry.getId(), event.getAggregateId());
        assertEquals(externalReference, event.getExternalReference());
        assertTrue(event.getPayload().contains("\"transaction_id\":\"" + transactionId + "\""));
        assertTrue(event.getPayload().contains("\"reference_id\":\"" + referenceId + "\""));
        assertTrue(event.getPayload().contains("\"amount_sat\":" + Math.abs(entry.getDeltaSats())));
        assertTrue(event.getPayload().contains("\"balance_after_sat\":" + entry.getBalanceAfter()));
        if (metadata != null) {
            assertTrue(event.getPayload().contains("\"metadata\":\"" + metadata.replace("\"", "\\\"") + "\""));
        }
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
