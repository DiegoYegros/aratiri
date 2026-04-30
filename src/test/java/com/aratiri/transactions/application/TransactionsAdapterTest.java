package com.aratiri.transactions.application;

import com.aratiri.infrastructure.messaging.outbox.OutboxWriter;
import com.aratiri.infrastructure.persistence.jpa.entity.TransactionEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.TransactionEventEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.TransactionEventType;
import com.aratiri.infrastructure.persistence.jpa.repository.LightningInvoiceRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.TransactionEventRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.TransactionsRepository;
import com.aratiri.infrastructure.persistence.ledger.AccountLedgerService;
import com.aratiri.shared.exception.AratiriException;
import com.aratiri.transactions.application.dto.*;
import com.aratiri.webhooks.application.WebhookEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionsAdapterTest {

    @Mock
    private TransactionsRepository transactionsRepository;

    @Mock
    private TransactionEventRepository transactionEventRepository;

    @Mock
    private LightningInvoiceRepository lightningInvoiceRepository;

    @Mock
    private OutboxWriter outboxWriter;

    @Mock
    private AccountLedgerService accountLedgerService;

    @Mock
    private WebhookEventService webhookEventService;

    private TransactionsAdapter transactionsAdapter;
    private TransactionSettlementService transactionSettlementService;

    private static final String USER_ID = "user-123";
    private static final String TRANSACTION_ID = "tx-456";

    @BeforeEach
    void setUp() {
        transactionSettlementService = new TransactionSettlementService(
                transactionsRepository,
                transactionEventRepository,
                accountLedgerService,
                outboxWriter,
                webhookEventService,
                lightningInvoiceRepository
        );
        transactionsAdapter = new TransactionsAdapter(
                transactionsRepository,
                transactionSettlementService,
                transactionSettlementService
        );
    }

    @Test
    void createTransaction_shouldPersistStatusEventAndReturnPending() {
        CreateTransactionRequest request = CreateTransactionRequest.builder()
                .userId(USER_ID)
                .amountSat(1000L)
                .currency(TransactionCurrency.BTC)
                .type(TransactionType.LIGHTNING_DEBIT)
                .status(TransactionStatus.PENDING)
                .description("Test payment")
                .referenceId("ref-123")
                .build();

        TransactionEntity saved = new TransactionEntity();
        saved.setId(TRANSACTION_ID);
        saved.setUserId(USER_ID);
        saved.setAmount(1000L);
        saved.setCurrency(TransactionCurrency.BTC);
        saved.setType(TransactionType.LIGHTNING_DEBIT);
        saved.setDescription("Test payment");
        saved.setReferenceId("ref-123");
        saved.setCreatedAt(Instant.now());

        when(transactionsRepository.save(any(TransactionEntity.class))).thenReturn(saved);
        when(transactionEventRepository.findByTransaction_IdOrderByCreatedAtAsc(TRANSACTION_ID))
                .thenReturn(List.of(statusEvent(saved, TransactionStatus.PENDING)));

        TransactionDTOResponse response = transactionsAdapter.createTransaction(request);

        assertEquals(TransactionStatus.PENDING, response.getStatus());
        verify(transactionEventRepository).save(any(TransactionEventEntity.class));
    }

    @Test
    void confirmTransaction_shouldCompleteAndPublishPaymentSent() {
        TransactionEntity pendingTx = new TransactionEntity();
        pendingTx.setId(TRANSACTION_ID);
        pendingTx.setUserId(USER_ID);
        pendingTx.setAmount(1500L);
        pendingTx.setCurrentAmount(1500L);
        pendingTx.setCurrency(TransactionCurrency.BTC);
        pendingTx.setType(TransactionType.LIGHTNING_DEBIT);
        pendingTx.setReferenceId("payhash");
        pendingTx.setCreatedAt(Instant.now());

        when(transactionsRepository.findById(TRANSACTION_ID)).thenReturn(Optional.of(pendingTx));
        when(accountLedgerService.appendLightningDebitSettlement(pendingTx)).thenReturn(8500L);

        List<TransactionEventEntity> pendingEvents = List.of(statusEvent(pendingTx, TransactionStatus.PENDING));
        List<TransactionEventEntity> completedEvents = List.of(
                statusEvent(pendingTx, TransactionStatus.PENDING),
                statusEvent(pendingTx, TransactionStatus.COMPLETED)
        );
        when(transactionEventRepository.findByTransaction_IdOrderByCreatedAtAsc(TRANSACTION_ID))
                .thenReturn(pendingEvents, completedEvents);

        TransactionDTOResponse response = transactionsAdapter.confirmTransaction(TRANSACTION_ID, USER_ID);

        assertEquals(TransactionStatus.COMPLETED, response.getStatus());
        verify(outboxWriter).publishPaymentSent(eq(TRANSACTION_ID), any());
        verify(accountLedgerService).appendLightningDebitSettlement(pendingTx);
    }

    @Test
    void confirmTransaction_shouldRejectAnotherUsersDebit() {
        TransactionEntity pendingTx = new TransactionEntity();
        pendingTx.setId(TRANSACTION_ID);
        pendingTx.setUserId("other-user");
        pendingTx.setAmount(1500L);
        pendingTx.setCurrentAmount(1500L);
        pendingTx.setCurrency(TransactionCurrency.BTC);
        pendingTx.setType(TransactionType.LIGHTNING_DEBIT);
        pendingTx.setCreatedAt(Instant.now());

        when(transactionsRepository.findById(TRANSACTION_ID)).thenReturn(Optional.of(pendingTx));

        AratiriException exception = assertThrows(
                AratiriException.class,
                () -> transactionsAdapter.confirmTransaction(TRANSACTION_ID, USER_ID)
        );

        assertTrue(exception.getMessage().contains("does not correspond to current user"));
        verifyNoInteractions(accountLedgerService);
    }

    @Test
    void failTransaction_shouldAppendFailedEvent() {
        TransactionEntity pendingTx = new TransactionEntity();
        pendingTx.setId(TRANSACTION_ID);
        pendingTx.setUserId(USER_ID);
        pendingTx.setAmount(1000L);
        pendingTx.setType(TransactionType.LIGHTNING_DEBIT);

        when(transactionsRepository.findById(TRANSACTION_ID)).thenReturn(Optional.of(pendingTx));
        when(transactionEventRepository.findByTransaction_IdOrderByCreatedAtAsc(TRANSACTION_ID))
                .thenReturn(List.of(statusEvent(pendingTx, TransactionStatus.PENDING)));

        transactionsAdapter.failTransaction(TRANSACTION_ID, "failure");

        verify(transactionEventRepository).save(any(TransactionEventEntity.class));
    }

    @Test
    void createAndSettleTransaction_shouldRejectNonSettleableTypes() {
        CreateTransactionRequest request = CreateTransactionRequest.builder()
                .userId(USER_ID)
                .amountSat(1000L)
                .type(TransactionType.LIGHTNING_DEBIT)
                .build();

        assertThrows(AratiriException.class, () -> transactionsAdapter.createAndSettleTransaction(request));
    }

    @Test
    void existsByReferenceId_shouldReturnTrueIfExists() {
        when(transactionsRepository.existsByReferenceId("ref-123")).thenReturn(true);

        assertTrue(transactionsAdapter.existsByReferenceId("ref-123"));
    }

    @Test
    void confirmTransactionAsAdmin_shouldCompleteExternalDebit() {
        TransactionEntity externalDebitTx = new TransactionEntity();
        externalDebitTx.setId(TRANSACTION_ID);
        externalDebitTx.setUserId(USER_ID);
        externalDebitTx.setAmount(1500L);
        externalDebitTx.setCurrentAmount(1500L);
        externalDebitTx.setCurrency(TransactionCurrency.BTC);
        externalDebitTx.setType(TransactionType.ONCHAIN_DEBIT);
        externalDebitTx.setReferenceId("payhash");
        externalDebitTx.setCreatedAt(Instant.now());

        when(transactionsRepository.findById(TRANSACTION_ID)).thenReturn(Optional.of(externalDebitTx));
        when(accountLedgerService.appendOnChainDebitSettlement(externalDebitTx)).thenReturn(8500L);

        List<TransactionEventEntity> pendingEvents = List.of(statusEvent(externalDebitTx, TransactionStatus.PENDING));
        List<TransactionEventEntity> completedEvents = List.of(
                statusEvent(externalDebitTx, TransactionStatus.PENDING),
                statusEvent(externalDebitTx, TransactionStatus.COMPLETED)
        );
        when(transactionEventRepository.findByTransaction_IdOrderByCreatedAtAsc(TRANSACTION_ID))
                .thenReturn(pendingEvents, completedEvents);

        TransactionDTOResponse response = transactionsAdapter.confirmTransactionAsAdmin(TRANSACTION_ID);

        assertEquals(TransactionStatus.COMPLETED, response.getStatus());
    }

    @Test
    void confirmTransactionAsAdmin_shouldSettleNonDebit() {
        TransactionEntity creditTx = new TransactionEntity();
        creditTx.setId(TRANSACTION_ID);
        creditTx.setUserId(USER_ID);
        creditTx.setAmount(1000L);
        creditTx.setCurrency(TransactionCurrency.BTC);
        creditTx.setType(TransactionType.LIGHTNING_CREDIT);
        creditTx.setDescription("Invoice paid");
        creditTx.setReferenceId("payhash");
        creditTx.setCreatedAt(Instant.now());

        when(transactionsRepository.findById(TRANSACTION_ID)).thenReturn(Optional.of(creditTx));
        when(accountLedgerService.appendLightningCreditSettlement(creditTx)).thenReturn(5000L);

        List<TransactionEventEntity> pendingEvents = List.of(statusEvent(creditTx, TransactionStatus.PENDING));
        List<TransactionEventEntity> completedEvents = List.of(
                statusEvent(creditTx, TransactionStatus.PENDING),
                statusEvent(creditTx, TransactionStatus.COMPLETED)
        );
        when(transactionEventRepository.findByTransaction_IdOrderByCreatedAtAsc(TRANSACTION_ID))
                .thenReturn(pendingEvents, completedEvents);

        TransactionDTOResponse response = transactionsAdapter.confirmTransactionAsAdmin(TRANSACTION_ID);

        assertEquals(TransactionStatus.COMPLETED, response.getStatus());
    }

    @Test
    void failTransaction_shouldFailExternalDebit() {
        TransactionEntity externalDebitTx = new TransactionEntity();
        externalDebitTx.setId(TRANSACTION_ID);
        externalDebitTx.setUserId(USER_ID);
        externalDebitTx.setAmount(1000L);
        externalDebitTx.setType(TransactionType.ONCHAIN_DEBIT);
        externalDebitTx.setCreatedAt(Instant.now());

        when(transactionsRepository.findById(TRANSACTION_ID)).thenReturn(Optional.of(externalDebitTx));
        when(transactionEventRepository.findByTransaction_IdOrderByCreatedAtAsc(TRANSACTION_ID))
                .thenReturn(List.of(statusEvent(externalDebitTx, TransactionStatus.PENDING)));

        transactionsAdapter.failTransaction(TRANSACTION_ID, "failure");

        verify(transactionEventRepository).save(any(TransactionEventEntity.class));
    }

    private TransactionEventEntity statusEvent(TransactionEntity tx, TransactionStatus status) {
        return TransactionEventEntity.builder()
                .transaction(tx)
                .eventType(TransactionEventType.STATUS_CHANGED)
                .status(status)
                .build();
    }
}
