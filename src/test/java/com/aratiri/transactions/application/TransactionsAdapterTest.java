package com.aratiri.transactions.application;

import com.aratiri.infrastructure.persistence.jpa.entity.TransactionEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.TransactionEventEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.TransactionEventType;
import com.aratiri.infrastructure.persistence.jpa.repository.LightningInvoiceRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.OutboxEventRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.TransactionEventRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.TransactionsRepository;
import com.aratiri.shared.exception.AratiriException;
import com.aratiri.transactions.application.dto.*;
import com.aratiri.transactions.application.processor.TransactionProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

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
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private JsonMapper jsonMapper;

    @Mock
    private TransactionProcessor debitProcessor;

    @Mock
    private TransactionProcessor creditProcessor;

    private TransactionsAdapter transactionsAdapter;

    private static final String USER_ID = "user-123";
    private static final String TRANSACTION_ID = "tx-456";

    @BeforeEach
    void setUp() {
        when(debitProcessor.supportedType()).thenReturn(TransactionType.LIGHTNING_DEBIT);
        when(creditProcessor.supportedType()).thenReturn(TransactionType.LIGHTNING_CREDIT);
        transactionsAdapter = new TransactionsAdapter(
                transactionsRepository,
                List.of(creditProcessor, debitProcessor),
                lightningInvoiceRepository,
                jsonMapper,
                outboxEventRepository,
                transactionEventRepository
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
    void confirmTransaction_shouldCompleteAndPublishPaymentSent() throws Exception {
        TransactionEntity pendingTx = new TransactionEntity();
        pendingTx.setId(TRANSACTION_ID);
        pendingTx.setUserId(USER_ID);
        pendingTx.setAmount(1500L);
        pendingTx.setCurrency(TransactionCurrency.BTC);
        pendingTx.setType(TransactionType.LIGHTNING_DEBIT);
        pendingTx.setReferenceId("payhash");
        pendingTx.setCreatedAt(Instant.now());

        when(transactionsRepository.findById(TRANSACTION_ID)).thenReturn(Optional.of(pendingTx));
        when(debitProcessor.process(pendingTx)).thenReturn(8500L);
        when(jsonMapper.writeValueAsString(any())).thenReturn("{}");

        List<TransactionEventEntity> pendingEvents = List.of(statusEvent(pendingTx, TransactionStatus.PENDING));
        List<TransactionEventEntity> completedEvents = List.of(
                statusEvent(pendingTx, TransactionStatus.PENDING),
                statusEvent(pendingTx, TransactionStatus.COMPLETED)
        );
        when(transactionEventRepository.findByTransaction_IdOrderByCreatedAtAsc(TRANSACTION_ID))
                .thenReturn(pendingEvents, completedEvents);

        TransactionDTOResponse response = transactionsAdapter.confirmTransaction(TRANSACTION_ID, USER_ID);

        assertEquals(TransactionStatus.COMPLETED, response.getStatus());
        verify(outboxEventRepository, atLeastOnce()).save(any());
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

    private TransactionEventEntity statusEvent(TransactionEntity tx, TransactionStatus status) {
        return TransactionEventEntity.builder()
                .transaction(tx)
                .eventType(TransactionEventType.STATUS_CHANGED)
                .status(status)
                .build();
    }
}
