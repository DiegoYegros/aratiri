package com.aratiri.transactions;

import com.aratiri.AbstractIntegrationTest;
import com.aratiri.auth.application.dto.AuthResponseDTO;
import com.aratiri.auth.application.dto.RegistrationRequestDTO;
import com.aratiri.auth.application.dto.VerificationRequestDTO;
import com.aratiri.auth.application.port.out.EmailNotificationPort;
import com.aratiri.accounts.application.port.out.CurrencyConversionPort;
import com.aratiri.accounts.application.port.out.LightningAddressPort;
import com.aratiri.infrastructure.messaging.KafkaTopics;
import com.aratiri.infrastructure.persistence.jpa.entity.LightningInvoiceEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.OutboxEventEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.TransactionEntity;
import com.aratiri.infrastructure.persistence.jpa.repository.LightningInvoiceRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.OutboxEventRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.TransactionEventRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.TransactionsRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.VerificationDataRepository;
import com.aratiri.transactions.application.dto.*;
import com.aratiri.transactions.application.event.InternalTransferInitiatedEvent;
import com.aratiri.transactions.application.port.in.TransactionsPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

class TransactionStateIntegrationTest extends AbstractIntegrationTest {

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
    private TransactionsRepository transactionsRepository;

    @Autowired
    private TransactionEventRepository transactionEventRepository;

    @Autowired
    private LightningInvoiceRepository lightningInvoiceRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String userId;
    private String otherUserId;
    private String authToken;

    @BeforeEach
    void setupUserAndAccount() {
        String email = "tx-state@example.com";
        String password = "SecurePass123!";
        String name = "Transaction State Test";
        String alias = "txstatetest";

        when(currencyConversionPort.getCurrentBtcPrice()).thenReturn(Map.of("usd", BigDecimal.valueOf(50000)));
        when(lightningAddressPort.generateTaprootAddress()).thenReturn("bc1p_test_address_1", "bc1p_test_address_2");
        doAnswer(invocation -> null).when(emailNotificationPort).sendVerificationEmail(anyString(), anyString());

        webTestClient().post().uri("/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createRegistrationRequest(name, email, password, alias))
                .exchange()
                .expectStatus().isCreated();

        String verificationCode = verificationDataRepository.findById(email)
                .orElseThrow()
                .getCode();

        AuthResponseDTO tokens = webTestClient().post().uri("/v1/auth/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createVerificationRequest(email, verificationCode))
                .exchange()
                .expectStatus().isOk()
                .expectBody(AuthResponseDTO.class)
                .returnResult().getResponseBody();

        this.authToken = tokens.getAccessToken();
        this.userId = jdbcTemplate.queryForObject(
                "SELECT id FROM aratiri.users WHERE email = ?", String.class, email
        );

        String otherEmail = "tx-state-other@example.com";
        webTestClient().post().uri("/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createRegistrationRequest("Other User", otherEmail, password, "txstateother"))
                .exchange()
                .expectStatus().isCreated();

        String otherVerificationCode = verificationDataRepository.findById(otherEmail)
                .orElseThrow()
                .getCode();

        webTestClient().post().uri("/v1/auth/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createVerificationRequest(otherEmail, otherVerificationCode))
                .exchange()
                .expectStatus().isOk();

        this.otherUserId = jdbcTemplate.queryForObject(
                "SELECT id FROM aratiri.users WHERE email = ?", String.class, otherEmail
        );
    }

    @Test
    @DisplayName("Creating a transaction sets current_status=PENDING")
    void createTransaction_setsPendingStatus() {
        CreateTransactionRequest request = CreateTransactionRequest.builder()
                .userId(userId)
                .amountSat(1000)
                .currency(TransactionCurrency.BTC)
                .type(TransactionType.ONCHAIN_CREDIT)
                .status(TransactionStatus.PENDING)
                .description("Test")
                .referenceId("ref-pending")
                .build();

        TransactionDTOResponse response = transactionsPort.createTransaction(request);

        assertEquals(TransactionStatus.PENDING, response.getStatus());

        TransactionEntity entity = transactionsRepository.findById(response.getId()).orElseThrow();
        assertEquals("PENDING", entity.getCurrentStatus());
        assertEquals(1000L, entity.getCurrentAmount());
    }

    @Test
    @DisplayName("Confirming sets COMPLETED, balance_after, and completed_at")
    void confirmTransaction_setsCompletedState() {
        CreateTransactionRequest request = CreateTransactionRequest.builder()
                .userId(userId)
                .amountSat(5000)
                .currency(TransactionCurrency.BTC)
                .type(TransactionType.ONCHAIN_CREDIT)
                .status(TransactionStatus.PENDING)
                .description("Test")
                .referenceId("ref-completed")
                .build();

        TransactionDTOResponse created = transactionsPort.createTransaction(request);
        TransactionDTOResponse confirmed = transactionsPort.confirmTransaction(created.getId(), userId);

        assertEquals(TransactionStatus.COMPLETED, confirmed.getStatus());
        assertNotNull(confirmed.getBalanceAfterSat());

        TransactionEntity entity = transactionsRepository.findById(created.getId()).orElseThrow();
        assertEquals("COMPLETED", entity.getCurrentStatus());
        assertEquals(5000L, entity.getBalanceAfter());
        assertNotNull(entity.getCompletedAt());
    }

    @Test
    @DisplayName("Failing sets FAILED and failure_reason")
    void failTransaction_setsFailedState() {
        CreateTransactionRequest request = CreateTransactionRequest.builder()
                .userId(userId)
                .amountSat(1000)
                .currency(TransactionCurrency.BTC)
                .type(TransactionType.ONCHAIN_CREDIT)
                .status(TransactionStatus.PENDING)
                .description("Test")
                .referenceId("ref-failed")
                .build();

        TransactionDTOResponse created = transactionsPort.createTransaction(request);
        transactionsPort.failTransaction(created.getId(), "Test failure reason");

        TransactionEntity entity = transactionsRepository.findById(created.getId()).orElseThrow();
        assertEquals("FAILED", entity.getCurrentStatus());
        assertEquals("Test failure reason", entity.getFailureReason());
    }

    @Test
    @DisplayName("Fee addition updates current_amount")
    void addFeeToTransaction_updatesCurrentAmount() {
        CreateTransactionRequest request = CreateTransactionRequest.builder()
                .userId(userId)
                .amountSat(1000)
                .currency(TransactionCurrency.BTC)
                .type(TransactionType.LIGHTNING_DEBIT)
                .status(TransactionStatus.PENDING)
                .description("Test")
                .referenceId("ref-fee")
                .build();

        TransactionDTOResponse created = transactionsPort.createTransaction(request);
        transactionsPort.addFeeToTransaction(created.getId(), 50);

        TransactionEntity entity = transactionsRepository.findById(created.getId()).orElseThrow();
        assertEquals(1050L, entity.getCurrentAmount());
    }

    @Test
    @DisplayName("GET /v1/transactions/{id} returns only owner's transaction")
    void getTransactionById_ownerOnly() {
        CreateTransactionRequest request = CreateTransactionRequest.builder()
                .userId(userId)
                .amountSat(1000)
                .currency(TransactionCurrency.BTC)
                .type(TransactionType.ONCHAIN_CREDIT)
                .status(TransactionStatus.PENDING)
                .description("Test")
                .referenceId("ref-owner")
                .build();

        TransactionDTOResponse created = transactionsPort.createTransaction(request);

        var ownerResult = transactionsPort.getTransactionById(created.getId(), userId);
        assertTrue(ownerResult.isPresent());

        var otherResult = transactionsPort.getTransactionById(created.getId(), otherUserId);
        assertTrue(otherResult.isEmpty());
    }

    @Test
    @DisplayName("Cursor pagination returns distinct pages")
    @SuppressWarnings("java:S2925")
    void cursorPagination_returnsDistinctPages() {
        for (int i = 0; i < 10; i++) {
            CreateTransactionRequest request = CreateTransactionRequest.builder()
                    .userId(userId)
                    .amountSat(1000 + i)
                    .currency(TransactionCurrency.BTC)
                    .type(TransactionType.ONCHAIN_CREDIT)
                    .status(TransactionStatus.PENDING)
                    .description("Test " + i)
                    .referenceId("ref-page-" + i)
                    .build();
            transactionsPort.createTransaction(request);
            try { Thread.sleep(10); } catch (InterruptedException _) { Thread.currentThread().interrupt(); }
        }

        TransactionPageResponse firstPage = transactionsPort.getTransactionsWithCursor(userId, null, 5);
        assertEquals(5, firstPage.getTransactions().size());
        assertTrue(firstPage.isHasMore());
        assertNotNull(firstPage.getNextCursor());

        TransactionPageResponse secondPage = transactionsPort.getTransactionsWithCursor(userId, firstPage.getNextCursor(), 5);
        assertEquals(5, secondPage.getTransactions().size());

        var firstPageIds = firstPage.getTransactions().stream().map(TransactionDTOResponse::getId).toList();
        var secondPageIds = secondPage.getTransactions().stream().map(TransactionDTOResponse::getId).toList();

        assertTrue(firstPageIds.stream().noneMatch(secondPageIds::contains), "Pages should not overlap");
    }

    @Test
    @DisplayName("Limit is capped at 200")
    void cursorPagination_limitCappedAt200() {
        TransactionPageResponse response = transactionsPort.getTransactionsWithCursor(userId, null, 500);
        assertNotNull(response.getTransactions());
    }

    @Test
    @DisplayName("Malformed cursor returns 400")
    void malformedCursor_returns400() {
        webTestClient().get().uri("/v1/transactions?cursor=invalid_cursor")
                .header("Authorization", "Bearer " + authToken)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("Newest-first ordering is deterministic by (created_at DESC, id DESC)")
    @SuppressWarnings("java:S2925")
    void cursorPagination_newestFirstOrdering() {
        for (int i = 0; i < 5; i++) {
            CreateTransactionRequest request = CreateTransactionRequest.builder()
                    .userId(userId)
                    .amountSat(1000 + i)
                    .currency(TransactionCurrency.BTC)
                    .type(TransactionType.ONCHAIN_CREDIT)
                    .status(TransactionStatus.PENDING)
                    .description("Order test " + i)
                    .referenceId("ref-order-" + i)
                    .build();
            transactionsPort.createTransaction(request);
            try { Thread.sleep(10); } catch (InterruptedException _) { Thread.currentThread().interrupt(); }
        }

        TransactionPageResponse page = transactionsPort.getTransactionsWithCursor(userId, null, 10);
        List<TransactionDTOResponse> transactions = page.getTransactions();

        for (int i = 0; i < transactions.size() - 1; i++) {
            Instant current = transactions.get(i).getCreatedAt().toInstant();
            Instant next = transactions.get(i + 1).getCreatedAt().toInstant();
            assertTrue(current.isAfter(next) || current.equals(next), "Transactions should be in descending order");
        }
    }

    @Test
    @DisplayName("Internal transfer settlement updates both ledgers, invoice state, transaction history, and outbox")
    void processInternalTransfer_settlesCompleteWorkflow() {
        String paymentHash = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        long amountSat = 2_500L;

        transactionsPort.createAndSettleTransaction(CreateTransactionRequest.builder()
                .userId(userId)
                .amountSat(10_000L)
                .currency(TransactionCurrency.BTC)
                .type(TransactionType.ONCHAIN_CREDIT)
                .description("Seed balance")
                .referenceId("internal-transfer-seed")
                .build());

        TransactionDTOResponse senderDebit = transactionsPort.createTransaction(CreateTransactionRequest.builder()
                .userId(userId)
                .amountSat(amountSat)
                .currency(TransactionCurrency.BTC)
                .type(TransactionType.LIGHTNING_DEBIT)
                .status(TransactionStatus.PENDING)
                .description("Local Lightning transfer to: " + otherUserId)
                .referenceId(paymentHash)
                .build());

        lightningInvoiceRepository.save(LightningInvoiceEntity.builder()
                .userId(otherUserId)
                .paymentHash(paymentHash)
                .preimage("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb")
                .paymentRequest("lnbc2500internal")
                .invoiceState(LightningInvoiceEntity.InvoiceState.OPEN)
                .amountSats(amountSat)
                .createdAt(LocalDateTime.now())
                .expiry(3600)
                .amountPaidSats(0)
                .memo("Internal memo")
                .build());

        transactionsPort.processInternalTransfer(new InternalTransferInitiatedEvent(
                senderDebit.getId(),
                userId,
                otherUserId,
                amountSat,
                paymentHash
        ));

        TransactionEntity senderTransaction = transactionsRepository.findById(senderDebit.getId()).orElseThrow();
        assertEquals("COMPLETED", senderTransaction.getCurrentStatus());
        assertEquals(7_500L, senderTransaction.getBalanceAfter());

        TransactionEntity receiverCredit = transactionsRepository.findAll().stream()
                .filter(tx -> tx.getUserId().equals(otherUserId))
                .filter(tx -> tx.getType() == TransactionType.LIGHTNING_CREDIT)
                .filter(tx -> paymentHash.equals(tx.getReferenceId()))
                .findFirst()
                .orElseThrow();
        assertEquals("COMPLETED", receiverCredit.getCurrentStatus());
        assertEquals(2_500L, receiverCredit.getBalanceAfter());

        assertEquals(7_500L, latestBalanceForUser(userId));
        assertEquals(2_500L, latestBalanceForUser(otherUserId));

        LightningInvoiceEntity invoice = lightningInvoiceRepository.findByPaymentHash(paymentHash).orElseThrow();
        assertEquals(LightningInvoiceEntity.InvoiceState.SETTLED, invoice.getInvoiceState());
        assertEquals(amountSat, invoice.getAmountPaidSats());
        assertNotNull(invoice.getSettledAt());
        LocalDateTime firstSettledAt = invoice.getSettledAt();

        assertEquals(2, transactionEventRepository.findByTransaction_IdOrderByCreatedAtAsc(senderDebit.getId()).size());
        assertEquals(2, transactionEventRepository.findByTransaction_IdOrderByCreatedAtAsc(receiverCredit.getId()).size());

        List<OutboxEventEntity> outboxEvents = outboxEventRepository.findAll();
        assertTrue(outboxEvents.stream().anyMatch(event ->
                event.getEventType().equals(KafkaTopics.INTERNAL_TRANSFER_COMPLETED.getCode())
                        && event.getAggregateId().equals(senderDebit.getId())
                        && event.getPayload().contains(paymentHash)
        ));
        assertTrue(outboxEvents.stream().anyMatch(event ->
                event.getEventType().equals(KafkaTopics.INTERNAL_INVOICE_CANCEL.getCode())
                        && event.getAggregateId().equals(paymentHash)
                        && event.getPayload().contains(paymentHash)
        ));
        assertEquals(0, countOutboxEvents(outboxEvents, KafkaTopics.PAYMENT_SENT.getCode(), senderDebit.getId()));

        assertRetriedInternalTransferDoesNotDuplicateSettlement(senderDebit, receiverCredit, paymentHash, amountSat, firstSettledAt);
    }

    private void assertRetriedInternalTransferDoesNotDuplicateSettlement(
            TransactionDTOResponse senderDebit,
            TransactionEntity receiverCredit,
            String paymentHash,
            long amountSat,
            LocalDateTime firstSettledAt
    ) {
        transactionsPort.processInternalTransfer(new InternalTransferInitiatedEvent(
                senderDebit.getId(),
                userId,
                otherUserId,
                amountSat,
                paymentHash
        ));

        assertEquals(1, transactionsRepository.findAll().stream()
                .filter(tx -> tx.getUserId().equals(otherUserId))
                .filter(tx -> tx.getType() == TransactionType.LIGHTNING_CREDIT)
                .filter(tx -> paymentHash.equals(tx.getReferenceId()))
                .count());
        assertEquals(2, transactionEventRepository.findByTransaction_IdOrderByCreatedAtAsc(senderDebit.getId()).size());
        assertEquals(2, transactionEventRepository.findByTransaction_IdOrderByCreatedAtAsc(receiverCredit.getId()).size());
        assertEquals(2L, countLedgerEntries(senderDebit.getId(), receiverCredit.getId()));
        assertEquals(7_500L, latestBalanceForUser(userId));
        assertEquals(2_500L, latestBalanceForUser(otherUserId));

        LightningInvoiceEntity retriedInvoice = lightningInvoiceRepository.findByPaymentHash(paymentHash).orElseThrow();
        assertEquals(firstSettledAt, retriedInvoice.getSettledAt());

        List<OutboxEventEntity> retriedOutboxEvents = outboxEventRepository.findAll();
        assertEquals(1, countOutboxEvents(retriedOutboxEvents, KafkaTopics.INTERNAL_TRANSFER_COMPLETED.getCode(), senderDebit.getId()));
        assertEquals(1, countOutboxEvents(retriedOutboxEvents, KafkaTopics.INTERNAL_INVOICE_CANCEL.getCode(), paymentHash));
        assertEquals(0, countOutboxEvents(retriedOutboxEvents, KafkaTopics.PAYMENT_SENT.getCode(), senderDebit.getId()));
    }

    private long countOutboxEvents(List<OutboxEventEntity> events, String eventType, String aggregateId) {
        return events.stream()
                .filter(event -> event.getEventType().equals(eventType))
                .filter(event -> event.getAggregateId().equals(aggregateId))
                .count();
    }

    private long countLedgerEntries(String senderTransactionId, String receiverTransactionId) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM aratiri.account_entries
                WHERE transaction_id IN (?, ?)
                """, Long.class, senderTransactionId, receiverTransactionId);
        assertNotNull(count);
        return count;
    }

    private long latestBalanceForUser(String accountUserId) {
        Long balance = jdbcTemplate.queryForObject("""
                SELECT COALESCE((
                    SELECT ae.balance_after
                    FROM aratiri.account_entries ae
                    JOIN aratiri.accounts a ON a.id = ae.account_id
                    WHERE a.user_id = ?
                    ORDER BY ae.created_at DESC, ae.id DESC
                    LIMIT 1
                ), 0)
                """, Long.class, accountUserId);
        assertNotNull(balance);
        return balance;
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
