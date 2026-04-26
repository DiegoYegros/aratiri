package com.aratiri.transactions.application;

import com.aratiri.infrastructure.messaging.KafkaTopics;
import com.aratiri.infrastructure.persistence.jpa.entity.OutboxEventEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.TransactionEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.TransactionEventEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.TransactionEventType;
import com.aratiri.infrastructure.persistence.jpa.repository.OutboxEventRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.TransactionEventRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.TransactionsRepository;
import com.aratiri.payments.application.event.PaymentSentEvent;
import com.aratiri.shared.exception.AratiriException;
import com.aratiri.transactions.application.dto.CreateTransactionRequest;
import com.aratiri.transactions.application.dto.TransactionCurrency;
import com.aratiri.transactions.application.dto.TransactionStatus;
import com.aratiri.transactions.application.dto.TransactionType;
import com.aratiri.transactions.application.processor.TransactionProcessor;
import com.aratiri.webhooks.application.WebhookEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TransactionSettlementService implements TransactionSettlementModule {
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final Set<TransactionType> SETTLEABLE_TYPES = Set.of(
            TransactionType.LIGHTNING_CREDIT,
            TransactionType.ONCHAIN_CREDIT
    );
    private static final Set<TransactionType> PAYMENT_SENT_TYPES = Set.of(
            TransactionType.LIGHTNING_DEBIT,
            TransactionType.INVOICE_DEBIT,
            TransactionType.ONCHAIN_DEBIT
    );
    private static final Set<TransactionType> EXTERNAL_DEBIT_TYPES = Set.of(
            TransactionType.LIGHTNING_DEBIT,
            TransactionType.ONCHAIN_DEBIT
    );
    private static final String INTERNAL_TRANSFER_PREFIX = "Internal transfer";
    private static final String FAILURE_ACTION = "failure";

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final TransactionsRepository transactionsRepository;
    private final TransactionEventRepository transactionEventRepository;
    private final Map<TransactionType, TransactionProcessor> processors;
    private final JsonMapper jsonMapper;
    private final OutboxEventRepository outboxEventRepository;
    private final WebhookEventService webhookEventService;

    public TransactionSettlementService(
            TransactionsRepository transactionsRepository,
            TransactionEventRepository transactionEventRepository,
            List<TransactionProcessor> processorList,
            JsonMapper jsonMapper,
            OutboxEventRepository outboxEventRepository,
            WebhookEventService webhookEventService
    ) {
        this.transactionsRepository = transactionsRepository;
        this.transactionEventRepository = transactionEventRepository;
        this.processors = processorList.stream()
                .collect(Collectors.toMap(TransactionProcessor::supportedType, Function.identity()));
        this.jsonMapper = jsonMapper;
        this.outboxEventRepository = outboxEventRepository;
        this.webhookEventService = webhookEventService;
    }

    public TransactionState createTransaction(CreateTransactionRequest request) {
        TransactionEntity transaction = buildTransactionEntity(request);
        TransactionStatus initialStatus = request.getStatus() == null ? TransactionStatus.PENDING : request.getStatus();
        transaction.setCurrentStatus(initialStatus.name());
        transaction.setCurrentAmount(request.getAmountSat());
        TransactionEntity savedTransaction = transactionsRepository.save(transaction);
        appendStatusEvent(savedTransaction, initialStatus, null, null);
        logger.info("Successfully created new transaction record with status [{}]. Transaction: [{}]",
                initialStatus, savedTransaction);
        return currentState(savedTransaction);
    }

    public TransactionState createAndSettleTransaction(CreateTransactionRequest request) {
        if (!SETTLEABLE_TYPES.contains(request.getType())) {
            throw new AratiriException(
                    String.format("Transaction type [%s] is not valid for the create-and-settle flow.", request.getType()),
                    HttpStatus.BAD_REQUEST.value()
            );
        }
        TransactionEntity transaction = buildTransactionEntity(request);
        transaction.setCurrentStatus(TransactionStatus.PENDING.name());
        transaction.setCurrentAmount(request.getAmountSat());
        TransactionEntity savedTransaction = transactionsRepository.save(transaction);
        appendStatusEvent(savedTransaction, TransactionStatus.PENDING, null, null);
        return settlePending(savedTransaction);
    }

    @Override
    @Transactional
    public TransactionSettlementResult settleInvoiceCredit(InvoiceCreditSettlement settlement) {
        TransactionState existingSettlement = currentStateForReference(settlement.paymentHash());
        if (existingSettlement != null) {
            createInvoiceSettledWebhook(existingSettlement, settlement.paymentHash());
            return TransactionSettlementResult.from(existingSettlement);
        }

        CreateTransactionRequest request = new CreateTransactionRequest(
                settlement.userId(),
                settlement.amountSat(),
                TransactionCurrency.BTC,
                TransactionType.LIGHTNING_CREDIT,
                TransactionStatus.COMPLETED,
                settlement.description(),
                settlement.paymentHash(),
                settlement.externalReference(),
                settlement.metadata()
        );
        TransactionState settled = createAndSettleTransaction(request);
        createInvoiceSettledWebhook(settled, settlement.paymentHash());
        return TransactionSettlementResult.from(settled);
    }

    @Override
    @Transactional
    public TransactionSettlementResult settleOnChainCredit(OnChainCreditSettlement settlement) {
        String referenceId = settlement.referenceId();
        TransactionState existingSettlement = currentStateForReference(referenceId);
        if (existingSettlement != null) {
            createOnChainDepositConfirmedWebhook(existingSettlement);
            return TransactionSettlementResult.from(existingSettlement);
        }

        CreateTransactionRequest request = new CreateTransactionRequest(
                settlement.userId(),
                settlement.amountSat(),
                TransactionCurrency.BTC,
                TransactionType.ONCHAIN_CREDIT,
                TransactionStatus.COMPLETED,
                "On-chain payment received",
                referenceId,
                null,
                null
        );
        TransactionState settled = createAndSettleTransaction(request);
        createOnChainDepositConfirmedWebhook(settled);
        return TransactionSettlementResult.from(settled);
    }

    @Override
    @Transactional
    public TransactionSettlementResult settleExternalDebit(ExternalDebitCompletionSettlement settlement) {
        TransactionEntity transaction = loadTransaction(settlement.transactionId(), "confirmation");
        requireExternalDebit(transaction, "confirmation");
        requireUserIfPresent(transaction, settlement.userId());
        return TransactionSettlementResult.from(settlePending(transaction));
    }

    @Override
    @Transactional
    public TransactionSettlementResult failExternalDebit(ExternalDebitFailureSettlement settlement) {
        TransactionEntity transaction = loadTransaction(settlement.transactionId(), FAILURE_ACTION);
        requireExternalDebit(transaction, FAILURE_ACTION);
        failPending(transaction, settlement.failureReason());
        return TransactionSettlementResult.from(currentState(transaction));
    }

    @Override
    @Transactional
    public void applyLightningRoutingFee(LightningRoutingFeeSettlement settlement) {
        addFeeToTransaction(settlement.transactionId(), settlement.feeSat());
    }

    public TransactionState settlePending(TransactionEntity transaction) {
        return settlePending(transaction, true);
    }

    public TransactionState settleInternalTransferDebit(TransactionEntity transaction) {
        return settlePending(transaction, false);
    }

    private TransactionState settlePending(TransactionEntity transaction, boolean createPaymentSucceededWebhook) {
        String id = transaction.getId();
        TransactionState state = currentState(transaction);
        if (state.status() == TransactionStatus.COMPLETED) {
            logger.info("Transaction [{}] already COMPLETED, returning current state", id);
            return state;
        }
        if (!state.isPending()) {
            throw new AratiriException(String.format("Transaction status [%s] is not valid for confirmation.", state.status()));
        }
        TransactionProcessor processor = processors.get(transaction.getType());
        if (processor == null) {
            throw new IllegalStateException("No processor configured for type: " + transaction.getType());
        }
        long newBalanceSat = processor.process(transaction);
        appendStatusEvent(transaction, TransactionStatus.COMPLETED, newBalanceSat, null);
        transaction.setCurrentStatus(STATUS_COMPLETED);
        transaction.setBalanceAfter(newBalanceSat);
        transaction.setCompletedAt(Instant.now());
        transactionsRepository.save(transaction);
        logger.info("Recorded COMPLETED event for transaction [{}]", id);
        if (createPaymentSucceededWebhook) {
            webhookEventService.createPaymentSucceededEvent(transaction);
        }
        return currentState(transaction);
    }

    public void failTransaction(String transactionId, String failureReason) {
        logger.warn("Failing transaction [{}]. Reason: {}", transactionId, failureReason);
        TransactionEntity transaction = loadTransaction(transactionId, FAILURE_ACTION);
        failPending(transaction, failureReason);
    }

    private void failPending(TransactionEntity transaction, String failureReason) {
        String transactionId = transaction.getId();
        TransactionState state = currentState(transaction);
        if (state.status() == TransactionStatus.FAILED) {
            logger.info("Transaction [{}] already FAILED, skipping", transactionId);
            return;
        }
        if (!state.isPending()) {
            logger.error("Attempted to fail a transaction that was not PENDING. ID: {}, Current Status: {}",
                    transactionId, state.status());
            throw new AratiriException(String.format("Transaction status [%s] is not valid for failure.", state.status()));
        }
        appendStatusEvent(transaction, TransactionStatus.FAILED, null, failureReason);
        transaction.setCurrentStatus(TransactionStatus.FAILED.name());
        transaction.setFailureReason(failureReason);
        transactionsRepository.save(transaction);
        logger.info("Transaction [{}] has been marked as FAILED.", transactionId);
        webhookEventService.createPaymentFailedEvent(transaction, failureReason);
    }

    public void addFeeToTransaction(String transactionId, long feeSat) {
        if (feeSat <= 0) {
            return;
        }
        TransactionEntity transaction = transactionsRepository.findById(transactionId)
                .orElseThrow(() -> new AratiriException(String.format("Transaction with id [%s] not found for fee update.", transactionId)));
        TransactionState state = currentState(transaction);
        if (!state.isPending()) {
            logger.error("Attempted to add a routing fee to a transaction that was not PENDING. ID: {}, Current Status: {}",
                    transactionId, state.status());
            throw new AratiriException(String.format("Transaction status [%s] is not valid for fee update.", state.status()));
        }
        if (transaction.getType() != TransactionType.LIGHTNING_DEBIT) {
            logger.error("Attempted to add a routing fee to a transaction of type [{}]. Only LIGHTNING_DEBIT is supported.",
                    transaction.getType());
            throw new AratiriException("Routing fees can only be applied to Lightning debit transactions.");
        }
        boolean hasFeeEvent = state.events().stream()
                .anyMatch(e -> e.getEventType() == TransactionEventType.FEE_ADDED);
        if (hasFeeEvent) {
            logger.info("Fee event already exists for transaction [{}], skipping", transactionId);
            return;
        }
        TransactionEventEntity event = TransactionEventEntity.builder()
                .transaction(transaction)
                .eventType(TransactionEventType.FEE_ADDED)
                .amountDelta(feeSat)
                .build();
        transactionEventRepository.save(event);
        transaction.setCurrentAmount(transaction.getCurrentAmount() + feeSat);
        transactionsRepository.save(transaction);
        logger.info("Added [{}] sats in routing fees to transaction [{}].", feeSat, transactionId);
    }

    public TransactionState currentState(TransactionEntity transaction) {
        List<TransactionEventEntity> events = transactionEventRepository.findByTransaction_IdOrderByCreatedAtAsc(transaction.getId());
        return TransactionState.from(transaction, events);
    }

    public Map<String, List<TransactionEventEntity>> eventsByTransaction(List<TransactionEntity> transactions) {
        if (transactions.isEmpty()) {
            return Collections.emptyMap();
        }
        List<String> ids = transactions.stream().map(TransactionEntity::getId).toList();
        List<TransactionEventEntity> events = transactionEventRepository.findByTransaction_IdInOrderByCreatedAtAsc(ids);
        return events.stream().collect(Collectors.groupingBy(event -> event.getTransaction().getId()));
    }

    private TransactionEntity buildTransactionEntity(CreateTransactionRequest request) {
        TransactionEntity transaction = new TransactionEntity();
        transaction.setUserId(request.getUserId());
        transaction.setAmount(request.getAmountSat());
        transaction.setCurrency(request.getCurrency());
        transaction.setType(request.getType());
        transaction.setDescription(request.getDescription());
        transaction.setReferenceId(request.getReferenceId());
        transaction.setExternalReference(request.getExternalReference());
        transaction.setMetadata(request.getMetadata());
        return transaction;
    }

    private TransactionEntity loadTransaction(String transactionId, String action) {
        return transactionsRepository.findById(transactionId)
                .orElseThrow(() -> new AratiriException(String.format("Transaction with id [%s] not found for %s.", transactionId, action)));
    }

    private void requireExternalDebit(TransactionEntity transaction, String action) {
        if (!EXTERNAL_DEBIT_TYPES.contains(transaction.getType())) {
            throw new AratiriException(String.format(
                    "Transaction type [%s] is not valid for external debit %s.",
                    transaction.getType(),
                    action
            ));
        }
    }

    private void requireUserIfPresent(TransactionEntity transaction, String userId) {
        if (userId != null && !userId.equals(transaction.getUserId())) {
            throw new AratiriException(String.format("Transaction [%s] does not correspond to current user.", transaction.getId()));
        }
    }

    private TransactionState currentStateForReference(String referenceId) {
        return transactionsRepository.findFirstByReferenceIdOrderByCreatedAtDesc(referenceId)
                .map(this::currentState)
                .orElse(null);
    }

    private void createInvoiceSettledWebhook(TransactionState settlement, String paymentHash) {
        if (settlement.status() == TransactionStatus.COMPLETED) {
            webhookEventService.createInvoiceSettledEvent(settlement.transaction(), paymentHash);
        }
    }

    private void createOnChainDepositConfirmedWebhook(TransactionState settlement) {
        if (settlement.status() == TransactionStatus.COMPLETED) {
            webhookEventService.createOnchainDepositConfirmedEvent(settlement.transaction());
        }
    }

    private void appendStatusEvent(TransactionEntity transaction, TransactionStatus status, Long balanceAfter, String details) {
        TransactionEventEntity event = TransactionEventEntity.builder()
                .transaction(transaction)
                .eventType(TransactionEventType.STATUS_CHANGED)
                .status(status)
                .balanceAfter(balanceAfter)
                .details(details)
                .build();
        transactionEventRepository.save(event);
        if (status == TransactionStatus.COMPLETED) {
            maybePublishPaymentSent(transaction);
        }
    }

    private void maybePublishPaymentSent(TransactionEntity transaction) {
        if (!PAYMENT_SENT_TYPES.contains(transaction.getType())) {
            return;
        }
        String description = transaction.getDescription();
        if (description != null && description.toLowerCase(Locale.ROOT).startsWith(INTERNAL_TRANSFER_PREFIX.toLowerCase(Locale.ROOT))) {
            return;
        }
        try {
            PaymentSentEvent eventPayload = new PaymentSentEvent(
                    transaction.getUserId(),
                    transaction.getId(),
                    transaction.getAmount(),
                    transaction.getReferenceId(),
                    LocalDateTime.now(),
                    transaction.getDescription()
            );
            OutboxEventEntity outboxEvent = OutboxEventEntity.builder()
                    .aggregateType("PAYMENT_SENT")
                    .aggregateId(transaction.getId())
                    .eventType(KafkaTopics.PAYMENT_SENT.getCode())
                    .payload(jsonMapper.writeValueAsString(eventPayload))
                    .build();
            outboxEventRepository.save(outboxEvent);
        } catch (Exception e) {
            logger.error("Failed to create outbox event for PaymentSentEvent", e);
        }
    }
}
