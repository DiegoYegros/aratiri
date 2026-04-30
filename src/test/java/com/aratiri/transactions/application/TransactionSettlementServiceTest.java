package com.aratiri.transactions.application;

import com.aratiri.infrastructure.messaging.outbox.OutboxWriter;
import com.aratiri.infrastructure.persistence.jpa.entity.LightningInvoiceEntity;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionSettlementServiceTest {

  @Mock
  private TransactionsRepository transactionsRepository;

  @Mock
  private TransactionEventRepository transactionEventRepository;

  @Mock
  private AccountLedgerService accountLedgerService;

  @Mock
  private OutboxWriter outboxWriter;

  @Mock
  private WebhookEventService webhookEventService;

  @Mock
  private LightningInvoiceRepository lightningInvoiceRepository;

  private TransactionSettlementService service;

  private static final String USER_ID = "user-123";
  private static final String TX_ID = "tx-456";

  @BeforeEach
  void setUp() {
    service = new TransactionSettlementService(
        transactionsRepository,
        transactionEventRepository,
        accountLedgerService,
        outboxWriter,
        webhookEventService,
        lightningInvoiceRepository
    );
  }

  // ── helpers ──

  private TransactionEntity pendingTx(String id, String userId, long amount, TransactionType type) {
    TransactionEntity tx = new TransactionEntity();
    tx.setId(id);
    tx.setUserId(userId);
    tx.setAmount(amount);
    tx.setCurrentAmount(amount);
    tx.setCurrency(TransactionCurrency.BTC);
    tx.setType(type);
    tx.setCurrentStatus(TransactionStatus.PENDING.name());
    tx.setReferenceId("ref-" + id);
    tx.setCreatedAt(Instant.now());
    return tx;
  }

  private TransactionEventEntity statusEvent(TransactionEntity tx, TransactionStatus status) {
    return TransactionEventEntity.builder()
        .transaction(tx)
        .eventType(TransactionEventType.STATUS_CHANGED)
        .status(status)
        .build();
  }

  private TransactionEventEntity completedEvent(TransactionEntity tx, Long balanceAfter) {
    return TransactionEventEntity.builder()
        .transaction(tx)
        .eventType(TransactionEventType.STATUS_CHANGED)
        .status(TransactionStatus.COMPLETED)
        .balanceAfter(balanceAfter)
        .build();
  }

  // ── createTransaction ──

  @Test
  void createTransaction_shouldPersistAndReturnState() {
    CreateTransactionRequest request = CreateTransactionRequest.builder()
        .userId(USER_ID)
        .amountSat(1000L)
        .currency(TransactionCurrency.BTC)
        .type(TransactionType.LIGHTNING_DEBIT)
        .status(TransactionStatus.PENDING)
        .description("Test payment")
        .referenceId("ref-123")
        .build();

    TransactionEntity saved = pendingTx(TX_ID, USER_ID, 1000L, TransactionType.LIGHTNING_DEBIT);
    when(transactionsRepository.save(any(TransactionEntity.class))).thenReturn(saved);
    when(transactionEventRepository.findByTransaction_IdOrderByCreatedAtAsc(TX_ID))
        .thenReturn(List.of(statusEvent(saved, TransactionStatus.PENDING)));

    TransactionState state = service.createTransaction(request);

    assertEquals(TransactionStatus.PENDING, state.status());
    assertEquals(1000L, state.amountSat());
    assertNotNull(state.transaction());
    verify(transactionEventRepository).save(any(TransactionEventEntity.class));
  }

  // ── createAndSettleTransaction ──

  @Test
  void createAndSettleTransaction_lightningCredit_success() {
    CreateTransactionRequest request = CreateTransactionRequest.builder()
        .userId(USER_ID)
        .amountSat(5000L)
        .currency(TransactionCurrency.BTC)
        .type(TransactionType.LIGHTNING_CREDIT)
        .status(TransactionStatus.COMPLETED)
        .description("Invoice settled")
        .referenceId("payhash")
        .build();

    TransactionEntity saved = pendingTx(TX_ID, USER_ID, 5000L, TransactionType.LIGHTNING_CREDIT);
    when(transactionsRepository.save(any(TransactionEntity.class))).thenReturn(saved);
    when(accountLedgerService.appendLightningCreditSettlement(saved)).thenReturn(15000L);
    when(transactionEventRepository.findByTransaction_IdOrderByCreatedAtAsc(TX_ID))
        .thenReturn(
            List.of(statusEvent(saved, TransactionStatus.PENDING)),
            List.of(statusEvent(saved, TransactionStatus.PENDING), completedEvent(saved, 15000L))
        );

    TransactionState state = service.createAndSettleTransaction(request);

    assertEquals(TransactionStatus.COMPLETED, state.status());
    verify(accountLedgerService).appendLightningCreditSettlement(saved);
    verify(webhookEventService).createPaymentSucceededEvent(any());
  }

  @Test
  void createAndSettleTransaction_onChainCredit_success() {
    CreateTransactionRequest request = CreateTransactionRequest.builder()
        .userId(USER_ID)
        .amountSat(2000L)
        .currency(TransactionCurrency.BTC)
        .type(TransactionType.ONCHAIN_CREDIT)
        .status(TransactionStatus.COMPLETED)
        .description("On-chain deposit")
        .referenceId("txref")
        .build();

    TransactionEntity saved = pendingTx(TX_ID, USER_ID, 2000L, TransactionType.ONCHAIN_CREDIT);
    when(transactionsRepository.save(any(TransactionEntity.class))).thenReturn(saved);
    when(accountLedgerService.appendOnChainCreditSettlement(saved)).thenReturn(12000L);
    when(transactionEventRepository.findByTransaction_IdOrderByCreatedAtAsc(TX_ID))
        .thenReturn(
            List.of(statusEvent(saved, TransactionStatus.PENDING)),
            List.of(statusEvent(saved, TransactionStatus.PENDING), completedEvent(saved, 12000L))
        );

    TransactionState state = service.createAndSettleTransaction(request);

    assertEquals(TransactionStatus.COMPLETED, state.status());
    verify(accountLedgerService).appendOnChainCreditSettlement(saved);
  }

  @Test
  void createAndSettleTransaction_nonSettleableType_throws() {
    CreateTransactionRequest request = CreateTransactionRequest.builder()
        .userId(USER_ID)
        .amountSat(1000L)
        .type(TransactionType.LIGHTNING_DEBIT)
        .build();

    AratiriException ex = assertThrows(AratiriException.class,
        () -> service.createAndSettleTransaction(request));

    assertTrue(ex.getMessage().contains("not valid for the create-and-settle flow"));
  }

  // ── settleInvoiceCredit ──

  @Test
  void settleInvoiceCredit_newSettlement() {
    InvoiceCreditSettlement settlement = new InvoiceCreditSettlement(
        USER_ID, 5000L, "payhash", "desc", null, null
    );

    when(transactionsRepository.findFirstByReferenceIdOrderByCreatedAtDesc("payhash"))
        .thenReturn(Optional.empty());
    TransactionEntity saved = pendingTx(TX_ID, USER_ID, 5000L, TransactionType.LIGHTNING_CREDIT);
    when(transactionsRepository.save(any(TransactionEntity.class))).thenReturn(saved);
    when(accountLedgerService.appendLightningCreditSettlement(saved)).thenReturn(10000L);
    when(transactionEventRepository.findByTransaction_IdOrderByCreatedAtAsc(TX_ID))
        .thenReturn(
            List.of(statusEvent(saved, TransactionStatus.PENDING)),
            List.of(statusEvent(saved, TransactionStatus.PENDING), completedEvent(saved, 10000L))
        );

    TransactionSettlementResult result = service.settleInvoiceCredit(settlement);

    assertEquals(TX_ID, result.transactionId());
    assertEquals(TransactionStatus.COMPLETED, result.status());
    verify(webhookEventService).createInvoiceSettledEvent(any());
  }

  @Test
  void settleInvoiceCredit_duplicateSettlement_returnsExisting() {
    InvoiceCreditSettlement settlement = new InvoiceCreditSettlement(
        USER_ID, 5000L, "payhash", "desc", null, null
    );

    TransactionEntity existing = pendingTx(TX_ID, USER_ID, 5000L, TransactionType.LIGHTNING_CREDIT);
    existing.setCurrentStatus(TransactionStatus.COMPLETED.name());
    existing.setBalanceAfter(10000L);

    when(transactionsRepository.findFirstByReferenceIdOrderByCreatedAtDesc("payhash"))
        .thenReturn(Optional.of(existing));
    when(transactionEventRepository.findByTransaction_IdOrderByCreatedAtAsc(TX_ID))
        .thenReturn(List.of(statusEvent(existing, TransactionStatus.PENDING), completedEvent(existing, 10000L)));

    TransactionSettlementResult result = service.settleInvoiceCredit(settlement);

    assertEquals(TX_ID, result.transactionId());
    assertEquals(TransactionStatus.COMPLETED, result.status());
    verify(webhookEventService).createInvoiceSettledEvent(any());
    verify(transactionsRepository, never()).save(any());
  }

  // ── settleOnChainCredit ──

  @Test
  void settleOnChainCredit_newSettlement() {
    OnChainCreditSettlement settlement = new OnChainCreditSettlement(USER_ID, 2000L, "txHash1", 0L);

    when(transactionsRepository.findFirstByReferenceIdOrderByCreatedAtDesc("txHash1:0"))
        .thenReturn(Optional.empty());
    TransactionEntity saved = pendingTx(TX_ID, USER_ID, 2000L, TransactionType.ONCHAIN_CREDIT);
    when(transactionsRepository.save(any(TransactionEntity.class))).thenReturn(saved);
    when(accountLedgerService.appendOnChainCreditSettlement(saved)).thenReturn(7000L);
    when(transactionEventRepository.findByTransaction_IdOrderByCreatedAtAsc(TX_ID))
        .thenReturn(
            List.of(statusEvent(saved, TransactionStatus.PENDING)),
            List.of(statusEvent(saved, TransactionStatus.PENDING), completedEvent(saved, 7000L))
        );

    TransactionSettlementResult result = service.settleOnChainCredit(settlement);

    assertEquals(TX_ID, result.transactionId());
    assertEquals(TransactionStatus.COMPLETED, result.status());
    verify(webhookEventService).createOnchainDepositConfirmedEvent(any());
  }

  @Test
  void settleOnChainCredit_duplicateSettlement_returnsExisting() {
    OnChainCreditSettlement settlement = new OnChainCreditSettlement(USER_ID, 2000L, "txHash1", 0L);

    TransactionEntity existing = pendingTx(TX_ID, USER_ID, 2000L, TransactionType.ONCHAIN_CREDIT);
    existing.setCurrentStatus(TransactionStatus.COMPLETED.name());
    existing.setBalanceAfter(7000L);

    when(transactionsRepository.findFirstByReferenceIdOrderByCreatedAtDesc("txHash1:0"))
        .thenReturn(Optional.of(existing));
    when(transactionEventRepository.findByTransaction_IdOrderByCreatedAtAsc(TX_ID))
        .thenReturn(List.of(statusEvent(existing, TransactionStatus.PENDING), completedEvent(existing, 7000L)));

    TransactionSettlementResult result = service.settleOnChainCredit(settlement);

    assertEquals(TransactionStatus.COMPLETED, result.status());
    verify(webhookEventService).createOnchainDepositConfirmedEvent(any());
    verify(transactionsRepository, never()).save(any());
  }

  // ── settleExternalDebit ──

  @Test
  void settleExternalDebit_success() {
    TransactionEntity tx = pendingTx(TX_ID, USER_ID, 1500L, TransactionType.LIGHTNING_DEBIT);
    when(transactionsRepository.findById(TX_ID)).thenReturn(Optional.of(tx));
    when(accountLedgerService.appendLightningDebitSettlement(tx)).thenReturn(5000L);
    when(transactionEventRepository.findByTransaction_IdOrderByCreatedAtAsc(TX_ID))
        .thenReturn(
            List.of(statusEvent(tx, TransactionStatus.PENDING)),
            List.of(statusEvent(tx, TransactionStatus.PENDING), completedEvent(tx, 5000L))
        );

    TransactionSettlementResult result = service.settleExternalDebit(
        new ExternalDebitCompletionSettlement(TX_ID, USER_ID)
    );

    assertEquals(TransactionStatus.COMPLETED, result.status());
    verify(accountLedgerService).appendLightningDebitSettlement(tx);
  }

  @Test
  void settleExternalDebit_nonExternalDebitType_throws() {
    TransactionEntity tx = pendingTx(TX_ID, USER_ID, 1000L, TransactionType.LIGHTNING_CREDIT);
    when(transactionsRepository.findById(TX_ID)).thenReturn(Optional.of(tx));
    ExternalDebitCompletionSettlement settlement = new ExternalDebitCompletionSettlement(TX_ID, USER_ID);

    AratiriException ex = assertThrows(AratiriException.class,
        () -> service.settleExternalDebit(settlement));

    assertTrue(ex.getMessage().contains("not valid for external debit"));
  }

  @Test
  void settleExternalDebit_wrongUser_throws() {
    TransactionEntity tx = pendingTx(TX_ID, "other-user", 1000L, TransactionType.LIGHTNING_DEBIT);
    when(transactionsRepository.findById(TX_ID)).thenReturn(Optional.of(tx));
    ExternalDebitCompletionSettlement settlement = new ExternalDebitCompletionSettlement(TX_ID, USER_ID);

    AratiriException ex = assertThrows(AratiriException.class,
        () -> service.settleExternalDebit(settlement));

    assertTrue(ex.getMessage().contains("does not correspond to current user"));
  }

  // ── failExternalDebit ──

  @Test
  void failExternalDebit_success() {
    TransactionEntity tx = pendingTx(TX_ID, USER_ID, 1500L, TransactionType.LIGHTNING_DEBIT);
    when(transactionsRepository.findById(TX_ID)).thenReturn(Optional.of(tx));
    when(transactionEventRepository.findByTransaction_IdOrderByCreatedAtAsc(TX_ID))
        .thenReturn(
            List.of(statusEvent(tx, TransactionStatus.PENDING)),
            List.of(statusEvent(tx, TransactionStatus.PENDING), statusEvent(tx, TransactionStatus.FAILED))
        );

    TransactionSettlementResult result = service.failExternalDebit(
        new ExternalDebitFailureSettlement(TX_ID, "network timeout")
    );

    assertEquals(TransactionStatus.FAILED, result.status());
    verify(webhookEventService).createPaymentFailedEvent(any());
  }

  @Test
  void failExternalDebit_nonExternalDebitType_throws() {
    TransactionEntity tx = pendingTx(TX_ID, USER_ID, 1000L, TransactionType.LIGHTNING_CREDIT);
    when(transactionsRepository.findById(TX_ID)).thenReturn(Optional.of(tx));
    ExternalDebitFailureSettlement settlement = new ExternalDebitFailureSettlement(TX_ID, "reason");

    assertThrows(AratiriException.class, () -> service.failExternalDebit(settlement));
  }

  @Test
  void failExternalDebit_alreadyFailed_skips() {
    TransactionEntity tx = pendingTx(TX_ID, USER_ID, 1500L, TransactionType.LIGHTNING_DEBIT);
    tx.setCurrentStatus(TransactionStatus.FAILED.name());
    tx.setFailureReason("previous failure");
    when(transactionsRepository.findById(TX_ID)).thenReturn(Optional.of(tx));
    when(transactionEventRepository.findByTransaction_IdOrderByCreatedAtAsc(TX_ID))
        .thenReturn(List.of(
            statusEvent(tx, TransactionStatus.PENDING),
            failedEvent(tx, "previous failure")
        ));

    service.failExternalDebit(new ExternalDebitFailureSettlement(TX_ID, "second failure"));

    verify(transactionsRepository, never()).save(any());
    verify(webhookEventService, never()).createPaymentFailedEvent(any());
  }

  private TransactionEventEntity failedEvent(TransactionEntity tx, String reason) {
    return TransactionEventEntity.builder()
        .transaction(tx)
        .eventType(TransactionEventType.STATUS_CHANGED)
        .status(TransactionStatus.FAILED)
        .details(reason)
        .build();
  }

  // ── applyLightningRoutingFee ──

  @Test
  void applyLightningRoutingFee_success() {
    TransactionEntity tx = pendingTx(TX_ID, USER_ID, 1500L, TransactionType.LIGHTNING_DEBIT);
    when(transactionsRepository.findById(TX_ID)).thenReturn(Optional.of(tx));
    when(transactionEventRepository.findByTransaction_IdOrderByCreatedAtAsc(TX_ID))
        .thenReturn(List.of(statusEvent(tx, TransactionStatus.PENDING)));

    service.applyLightningRoutingFee(new LightningRoutingFeeSettlement(TX_ID, 50L));

    verify(transactionEventRepository).save(any(TransactionEventEntity.class));
    verify(transactionsRepository).save(tx);
    assertEquals(1550L, tx.getCurrentAmount());
  }

  @Test
  void applyLightningRoutingFee_zeroFee_noOp() {
    service.applyLightningRoutingFee(new LightningRoutingFeeSettlement(TX_ID, 0L));

    verifyNoInteractions(transactionsRepository);
  }

  @Test
  void applyLightningRoutingFee_negativeFee_noOp() {
    service.applyLightningRoutingFee(new LightningRoutingFeeSettlement(TX_ID, -10L));

    verifyNoInteractions(transactionsRepository);
  }

  @Test
  void applyLightningRoutingFee_transactionNotFound_throws() {
    when(transactionsRepository.findById(TX_ID)).thenReturn(Optional.empty());
    LightningRoutingFeeSettlement settlement = new LightningRoutingFeeSettlement(TX_ID, 50L);

    assertThrows(AratiriException.class, () -> service.applyLightningRoutingFee(settlement));
  }

  @Test
  void applyLightningRoutingFee_nonPendingStatus_throws() {
    TransactionEntity tx = pendingTx(TX_ID, USER_ID, 1500L, TransactionType.LIGHTNING_DEBIT);
    tx.setCurrentStatus(TransactionStatus.COMPLETED.name());
    when(transactionsRepository.findById(TX_ID)).thenReturn(Optional.of(tx));
    when(transactionEventRepository.findByTransaction_IdOrderByCreatedAtAsc(TX_ID))
        .thenReturn(List.of(statusEvent(tx, TransactionStatus.PENDING), completedEvent(tx, 5000L)));
    LightningRoutingFeeSettlement settlement = new LightningRoutingFeeSettlement(TX_ID, 50L);

    assertThrows(AratiriException.class, () -> service.applyLightningRoutingFee(settlement));
  }

  @Test
  void applyLightningRoutingFee_nonLightningDebitType_throws() {
    TransactionEntity tx = pendingTx(TX_ID, USER_ID, 1500L, TransactionType.ONCHAIN_DEBIT);
    when(transactionsRepository.findById(TX_ID)).thenReturn(Optional.of(tx));
    when(transactionEventRepository.findByTransaction_IdOrderByCreatedAtAsc(TX_ID))
        .thenReturn(List.of(statusEvent(tx, TransactionStatus.PENDING)));
    LightningRoutingFeeSettlement settlement = new LightningRoutingFeeSettlement(TX_ID, 50L);

    assertThrows(AratiriException.class, () -> service.applyLightningRoutingFee(settlement));
  }

  @Test
  void applyLightningRoutingFee_duplicateFeeEvent_skips() {
    TransactionEntity tx = pendingTx(TX_ID, USER_ID, 1500L, TransactionType.LIGHTNING_DEBIT);
    when(transactionsRepository.findById(TX_ID)).thenReturn(Optional.of(tx));
    TransactionEventEntity existingFee = TransactionEventEntity.builder()
        .transaction(tx)
        .eventType(TransactionEventType.FEE_ADDED)
        .amountDelta(30L)
        .build();
    when(transactionEventRepository.findByTransaction_IdOrderByCreatedAtAsc(TX_ID))
        .thenReturn(List.of(statusEvent(tx, TransactionStatus.PENDING), existingFee));

    service.applyLightningRoutingFee(new LightningRoutingFeeSettlement(TX_ID, 50L));

    verify(transactionEventRepository, never()).save(any());
    verify(transactionsRepository, never()).save(any());
  }

  // ── settleInternalTransfer ──

  @Test
  void settleInternalTransfer_fullFlow() {
    String senderId = "sender-123";
    String receiverId = "receiver-456";
    String paymentHash = "internalHash";
    long amount = 1000L;

    TransactionEntity senderTx = pendingTx(TX_ID, senderId, amount, TransactionType.LIGHTNING_DEBIT);
    senderTx.setReferenceId(paymentHash);
    when(transactionsRepository.findById(TX_ID)).thenReturn(Optional.of(senderTx));

    TransactionEntity receiverTx = new TransactionEntity();
    receiverTx.setId("tx-receiver");
    receiverTx.setUserId(receiverId);
    receiverTx.setAmount(amount);
    receiverTx.setCurrentAmount(amount);
    receiverTx.setCurrency(TransactionCurrency.BTC);
    receiverTx.setType(TransactionType.LIGHTNING_CREDIT);
    receiverTx.setReferenceId(paymentHash);
    receiverTx.setCreatedAt(Instant.now());

    when(accountLedgerService.appendLightningDebitSettlement(senderTx)).thenReturn(8000L);
    when(accountLedgerService.appendLightningCreditSettlement(any(TransactionEntity.class))).thenReturn(12000L);

    LightningInvoiceEntity invoice = LightningInvoiceEntity.builder()
        .id("inv-1")
        .userId(receiverId)
        .paymentHash(paymentHash)
        .invoiceState(LightningInvoiceEntity.InvoiceState.OPEN)
        .memo("test memo")
        .build();
    when(lightningInvoiceRepository.findByPaymentHash(paymentHash)).thenReturn(Optional.of(invoice));
    when(lightningInvoiceRepository.save(any(LightningInvoiceEntity.class))).thenReturn(invoice);

    when(transactionsRepository.save(any(TransactionEntity.class)))
        .thenReturn(senderTx, receiverTx);

    when(transactionEventRepository.findByTransaction_IdOrderByCreatedAtAsc(TX_ID))
        .thenReturn(
            List.of(statusEvent(senderTx, TransactionStatus.PENDING)),
            List.of(statusEvent(senderTx, TransactionStatus.PENDING), completedEvent(senderTx, 8000L))
        );
    when(transactionEventRepository.findByTransaction_IdOrderByCreatedAtAsc("tx-receiver"))
        .thenReturn(
            List.of(statusEvent(receiverTx, TransactionStatus.PENDING)),
            List.of(statusEvent(receiverTx, TransactionStatus.PENDING), completedEvent(receiverTx, 12000L))
        );

    InternalTransferSettlement settlement = new InternalTransferSettlement(
        TX_ID, senderId, receiverId, amount, paymentHash
    );
    service.settleInternalTransfer(settlement);

    verify(accountLedgerService).appendLightningDebitSettlement(senderTx);
    verify(accountLedgerService).appendLightningCreditSettlement(any(TransactionEntity.class));
    verify(outboxWriter).publishInternalTransferCompleted(eq(TX_ID), any());
    verify(outboxWriter).publishInternalInvoiceCancel(eq(paymentHash), any());
    assertEquals(LightningInvoiceEntity.InvoiceState.SETTLED, invoice.getInvoiceState());
    assertEquals(amount, invoice.getAmountPaidSats());
  }

  @Test
  void settleInternalTransfer_wrongType_throws() {
    TransactionEntity tx = pendingTx(TX_ID, USER_ID, 1000L, TransactionType.LIGHTNING_CREDIT);
    when(transactionsRepository.findById(TX_ID)).thenReturn(Optional.of(tx));

    InternalTransferSettlement settlement = new InternalTransferSettlement(
        TX_ID, USER_ID, "receiver", 1000L, "hash"
    );

    AratiriException ex = assertThrows(AratiriException.class,
        () -> service.settleInternalTransfer(settlement));

    assertTrue(ex.getMessage().contains("not valid for internal transfer"));
  }

  @Test
  void settleInternalTransfer_wrongSender_throws() {
    TransactionEntity tx = pendingTx(TX_ID, "other-sender", 1000L, TransactionType.LIGHTNING_DEBIT);
    when(transactionsRepository.findById(TX_ID)).thenReturn(Optional.of(tx));

    InternalTransferSettlement settlement = new InternalTransferSettlement(
        TX_ID, USER_ID, "receiver", 1000L, "hash"
    );

    assertThrows(AratiriException.class, () -> service.settleInternalTransfer(settlement));
  }

  @Test
  void settleInternalTransfer_wrongAmount_throws() {
    TransactionEntity tx = pendingTx(TX_ID, USER_ID, 500L, TransactionType.LIGHTNING_DEBIT);
    when(transactionsRepository.findById(TX_ID)).thenReturn(Optional.of(tx));

    InternalTransferSettlement settlement = new InternalTransferSettlement(
        TX_ID, USER_ID, "receiver", 1000L, "hash"
    );

    assertThrows(AratiriException.class, () -> service.settleInternalTransfer(settlement));
  }

  @Test
  void settleInternalTransfer_wrongPaymentHash_throws() {
    TransactionEntity tx = pendingTx(TX_ID, USER_ID, 1000L, TransactionType.LIGHTNING_DEBIT);
    tx.setReferenceId("correct-hash");
    when(transactionsRepository.findById(TX_ID)).thenReturn(Optional.of(tx));

    InternalTransferSettlement settlement = new InternalTransferSettlement(
        TX_ID, USER_ID, "receiver", 1000L, "wrong-hash"
    );

    assertThrows(AratiriException.class, () -> service.settleInternalTransfer(settlement));
  }

  // ── settlePending – edge cases ──

  @Test
  void settlePending_alreadyCompleted_returnsCurrentState() {
    TransactionEntity tx = pendingTx(TX_ID, USER_ID, 1000L, TransactionType.LIGHTNING_DEBIT);
    tx.setCurrentStatus(TransactionStatus.COMPLETED.name());
    tx.setBalanceAfter(6000L);

    when(transactionEventRepository.findByTransaction_IdOrderByCreatedAtAsc(TX_ID))
        .thenReturn(List.of(statusEvent(tx, TransactionStatus.PENDING), completedEvent(tx, 6000L)));

    TransactionState state = service.settlePending(tx);

    assertEquals(TransactionStatus.COMPLETED, state.status());
    verifyNoInteractions(accountLedgerService);
    verify(transactionsRepository, never()).save(any());
  }

  @Test
  void settlePending_nonPendingStatus_throws() {
    TransactionEntity tx = pendingTx(TX_ID, USER_ID, 1000L, TransactionType.LIGHTNING_DEBIT);
    tx.setCurrentStatus(TransactionStatus.FAILED.name());
    tx.setFailureReason("already dead");

    when(transactionEventRepository.findByTransaction_IdOrderByCreatedAtAsc(TX_ID))
        .thenReturn(List.of(
            statusEvent(tx, TransactionStatus.PENDING),
            statusEvent(tx, TransactionStatus.FAILED)
        ));

    AratiriException ex = assertThrows(AratiriException.class,
        () -> service.settlePending(tx));

    assertTrue(ex.getMessage().contains("not valid for confirmation"));
  }

  // ── failTransaction ──

  @Test
  void failTransaction_nonPendingStatus_throws() {
    TransactionEntity tx = pendingTx(TX_ID, USER_ID, 1000L, TransactionType.LIGHTNING_DEBIT);
    tx.setCurrentStatus(TransactionStatus.COMPLETED.name());

    when(transactionsRepository.findById(TX_ID)).thenReturn(Optional.of(tx));
    when(transactionEventRepository.findByTransaction_IdOrderByCreatedAtAsc(TX_ID))
        .thenReturn(List.of(statusEvent(tx, TransactionStatus.PENDING), completedEvent(tx, 5000L)));

    AratiriException ex = assertThrows(AratiriException.class,
        () -> service.failTransaction(TX_ID, "should not fail completed"));

    assertTrue(ex.getMessage().contains("not valid for failure"));
  }

  @Test
  void failTransaction_alreadyFailed_skips() {
    TransactionEntity tx = pendingTx(TX_ID, USER_ID, 1000L, TransactionType.LIGHTNING_DEBIT);
    tx.setCurrentStatus(TransactionStatus.FAILED.name());
    tx.setFailureReason("previous");

    when(transactionsRepository.findById(TX_ID)).thenReturn(Optional.of(tx));
    when(transactionEventRepository.findByTransaction_IdOrderByCreatedAtAsc(TX_ID))
        .thenReturn(List.of(
            statusEvent(tx, TransactionStatus.PENDING),
            failedEvent(tx, "previous")
        ));

    service.failTransaction(TX_ID, "new reason");

    verify(transactionsRepository, never()).save(any());
    verify(webhookEventService, never()).createPaymentFailedEvent(any());
  }

  @Test
  void failTransaction_success() {
    TransactionEntity tx = pendingTx(TX_ID, USER_ID, 1000L, TransactionType.LIGHTNING_DEBIT);
    when(transactionsRepository.findById(TX_ID)).thenReturn(Optional.of(tx));
    when(transactionEventRepository.findByTransaction_IdOrderByCreatedAtAsc(TX_ID))
        .thenReturn(List.of(statusEvent(tx, TransactionStatus.PENDING)));

    service.failTransaction(TX_ID, "insufficient funds");

    assertEquals(TransactionStatus.FAILED.name(), tx.getCurrentStatus());
    assertEquals("insufficient funds", tx.getFailureReason());
    verify(webhookEventService).createPaymentFailedEvent(any());
  }

  // ── eventsByTransaction ──

  @Test
  void eventsByTransaction_emptyList_returnsEmptyMap() {
    var result = service.eventsByTransaction(List.of());

    assertTrue(result.isEmpty());
    verifyNoInteractions(transactionEventRepository);
  }

  @Test
  void eventsByTransaction_nonEmpty_returnsGrouped() {
    TransactionEntity tx1 = pendingTx("tx-1", USER_ID, 1000L, TransactionType.LIGHTNING_DEBIT);
    TransactionEntity tx2 = pendingTx("tx-2", USER_ID, 500L, TransactionType.LIGHTNING_CREDIT);

    TransactionEventEntity ev1 = statusEvent(tx1, TransactionStatus.PENDING);
    TransactionEventEntity ev2 = statusEvent(tx2, TransactionStatus.PENDING);
    TransactionEventEntity ev3 = completedEvent(tx2, 5000L);

    when(transactionEventRepository.findByTransaction_IdInOrderByCreatedAtAsc(List.of("tx-1", "tx-2")))
        .thenReturn(List.of(ev1, ev2, ev3));

    var result = service.eventsByTransaction(List.of(tx1, tx2));

    assertEquals(2, result.size());
    assertEquals(1, result.get("tx-1").size());
    assertEquals(2, result.get("tx-2").size());
  }
}
