package com.aratiri.transactions.application;

import com.aratiri.infrastructure.messaging.KafkaTopics;
import com.aratiri.infrastructure.persistence.jpa.entity.*;
import com.aratiri.infrastructure.persistence.jpa.repository.LightningInvoiceRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.OutboxEventRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.TransactionEventRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.TransactionsRepository;
import com.aratiri.shared.exception.AratiriException;
import com.aratiri.transactions.application.dto.*;
import com.aratiri.transactions.application.event.InternalTransferCompletedEvent;
import com.aratiri.transactions.application.event.InternalTransferInitiatedEvent;
import com.aratiri.transactions.application.port.in.TransactionsPort;
import com.aratiri.transactions.application.processor.TransactionProcessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import invoicesrpc.CancelInvoiceMsg;
import invoicesrpc.InvoicesGrpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TransactionsAdapter implements TransactionsPort {
    private static final Set<TransactionType> SETTLEABLE_TYPES = Set.of(
            TransactionType.LIGHTNING_CREDIT,
            TransactionType.ONCHAIN_CREDIT
    );
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final TransactionsRepository transactionsRepository;
    private final Map<TransactionType, TransactionProcessor> processors;
    private final LightningInvoiceRepository lightningInvoiceRepository;
    private final ObjectMapper objectMapper;
    private final InvoicesGrpc.InvoicesBlockingStub invoiceBlockingStub;
    private final OutboxEventRepository outboxEventRepository;
    private final TransactionEventRepository transactionEventRepository;

    public TransactionsAdapter(TransactionsRepository transactionsRepository, List<TransactionProcessor> processorList, LightningInvoiceRepository lightningInvoiceRepository, ObjectMapper objectMapper, InvoicesGrpc.InvoicesBlockingStub invoiceStub, OutboxEventRepository outboxEventRepository, TransactionEventRepository transactionEventRepository) {
        this.transactionsRepository = transactionsRepository;
        this.processors = processorList.stream()
                .collect(Collectors.toMap(TransactionProcessor::supportedType, Function.identity()));
        this.lightningInvoiceRepository = lightningInvoiceRepository;
        this.objectMapper = objectMapper;
        this.invoiceBlockingStub = invoiceStub;
        this.outboxEventRepository = outboxEventRepository;
        this.transactionEventRepository = transactionEventRepository;
    }

    @Override
    @Transactional
    public TransactionDTOResponse confirmTransaction(String id, String userId) {
        logger.info("In confirmTransaction, received id [{}]", id);
        TransactionEntity transaction = transactionsRepository.findById(id)
                .orElseThrow(() -> new AratiriException(String.format("Transaction with id [%s] not found.", id)));

        if (!Objects.equals(transaction.getUserId(), userId)) {
            throw new AratiriException(String.format("Transaction [%s] does not correspond to current user.", id));
        }
        TransactionAggregate aggregate = aggregateTransaction(transaction);
        if (!aggregate.isPending()) {
            throw new AratiriException(String.format("Transaction status [%s] is not valid for confirmation.", aggregate.status()));
        }
        TransactionProcessor processor = processors.get(transaction.getType());
        if (processor == null) {
            throw new IllegalStateException("No processor configured for type: " + transaction.getType());
        }
        long newBalanceSat = processor.process(transaction);
        appendStatusEvent(transaction, TransactionStatus.COMPLETED, newBalanceSat, null);
        logger.info("Recorded COMPLETED event for transaction [{}]", id);
        return mapToDto(aggregateTransaction(transaction));
    }

    @Override
    public boolean existsByReferenceId(String referenceId) {
        return transactionsRepository.existsByReferenceId(referenceId);
    }

    @Override
    @Transactional
    public TransactionDTOResponse createAndSettleTransaction(CreateTransactionRequest request) {
        logger.info("In createAndSettleTransaction. Received object [{}]", request);
        if (!SETTLEABLE_TYPES.contains(request.getType())) {
            throw new AratiriException(
                    String.format("Transaction type [%s] is not valid for the create-and-settle flow.", request.getType()),
                    HttpStatus.BAD_REQUEST.value()
            );
        }
        TransactionEntity transaction = buildTransactionEntity(request);
        TransactionProcessor processor = processors.get(transaction.getType());
        if (processor == null) {
            throw new AratiriException("No processor configured for type: " + transaction.getType());
        }
        TransactionEntity savedTransaction = transactionsRepository.save(transaction);
        appendStatusEvent(savedTransaction, TransactionStatus.PENDING, null, null);
        long newBalanceSat = processor.process(savedTransaction);
        appendStatusEvent(savedTransaction, TransactionStatus.COMPLETED, newBalanceSat, null);
        logger.info("Created transaction [{}] and appended COMPLETED event", savedTransaction.getId());
        return mapToDto(aggregateTransaction(savedTransaction));
    }


    @Override
    @Transactional
    public TransactionDTOResponse createTransaction(CreateTransactionRequest request) {
        logger.info("In createTransaction. Received request to create transaction: [{}]", request);
        TransactionEntity transaction = buildTransactionEntity(request);
        TransactionEntity savedTransaction = transactionsRepository.save(transaction);
        TransactionStatus initialStatus = Optional.ofNullable(request.getStatus()).orElse(TransactionStatus.PENDING);
        appendStatusEvent(savedTransaction, initialStatus, null, null);
        logger.info("Successfully created new transaction record with status [{}]. Transaction: [{}]",
                initialStatus, savedTransaction);
        return mapToDto(aggregateTransaction(savedTransaction));
    }

    @Override
    public List<TransactionDTOResponse> getTransactions(Instant from, Instant to, String userId) {
        List<TransactionEntity> transactions = transactionsRepository
                .findByUserIdAndCreatedAtBetween(userId, from, to);
        logger.info("Got a list of [{}] transactions from db", transactions.size());
        Map<String, List<TransactionEventEntity>> eventsByTransaction = groupEventsByTransaction(transactions);
        return transactions.stream()
                .map(tx -> mapToDto(TransactionAggregate.from(tx, eventsByTransaction.getOrDefault(tx.getId(), List.of()))))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void failTransaction(String transactionId, String failureReason) {
        logger.warn("Failing transaction [{}]. Reason: {}", transactionId, failureReason);
        TransactionEntity transaction = transactionsRepository.findById(transactionId)
                .orElseThrow(() -> new AratiriException(String.format("Transaction with id [%s] not found for failure.", transactionId)));
        TransactionAggregate aggregate = aggregateTransaction(transaction);
        if (!aggregate.isPending()) {
            logger.error("Attempted to fail a transaction that was not PENDING. ID: {}, Current Status: {}",
                    transactionId, aggregate.status());
            throw new AratiriException(String.format("Transaction status [%s] is not valid for failure.", aggregate.status()));
        }
        appendStatusEvent(transaction, TransactionStatus.FAILED, null, failureReason);
        logger.info("Transaction [{}] has been marked as FAILED.", transactionId);
    }

    @Override
    @Transactional
    public void addFeeToTransaction(String transactionId, long feeSat) {
        if (feeSat <= 0) {
            return;
        }
        TransactionEntity transaction = transactionsRepository.findById(transactionId)
                .orElseThrow(() -> new AratiriException(String.format("Transaction with id [%s] not found for fee update.", transactionId)));
        TransactionAggregate aggregate = aggregateTransaction(transaction);
        if (!aggregate.isPending()) {
            logger.error("Attempted to add a routing fee to a transaction that was not PENDING. ID: {}, Current Status: {}",
                    transactionId, aggregate.status());
            throw new AratiriException(String.format("Transaction status [%s] is not valid for fee update.", aggregate.status()));
        }
        if (transaction.getType() != TransactionType.LIGHTNING_DEBIT) {
            logger.error("Attempted to add a routing fee to a transaction of type [{}]. Only LIGHTNING_DEBIT is supported.",
                    transaction.getType());
            throw new AratiriException("Routing fees can only be applied to Lightning debit transactions.");
        }
        TransactionEventEntity event = TransactionEventEntity.builder()
                .transaction(transaction)
                .eventType(TransactionEventType.FEE_ADDED)
                .amountDelta(feeSat)
                .build();
        transactionEventRepository.save(event);
        logger.info("Added [{}] sats in routing fees to transaction [{}].", feeSat, transactionId);
    }

    private TransactionDTOResponse mapToDto(TransactionAggregate aggregate) {
        TransactionEntity transaction = aggregate.transaction();
        return TransactionDTOResponse.builder()
                .id(transaction.getId())
                .createdAt(OffsetDateTime.from(transaction.getCreatedAt().atZone(ZoneId.systemDefault())))
                .amountSat(aggregate.amountSat())
                .type(transaction.getType())
                .balanceAfterSat(aggregate.balanceAfterSat())
                .description(transaction.getDescription())
                .failureReason(aggregate.failureReason())
                .referenceId(transaction.getReferenceId())
                .status(aggregate.status())
                .currency(transaction.getCurrency())
                .build();
    }

    private TransactionAggregate aggregateTransaction(TransactionEntity transaction) {
        List<TransactionEventEntity> events = transactionEventRepository.findByTransaction_IdOrderByCreatedAtAsc(transaction.getId());
        return TransactionAggregate.from(transaction, events);
    }

    private Map<String, List<TransactionEventEntity>> groupEventsByTransaction(List<TransactionEntity> transactions) {
        if (transactions.isEmpty()) {
            return Collections.emptyMap();
        }
        List<String> ids = transactions.stream().map(TransactionEntity::getId).toList();
        List<TransactionEventEntity> events = transactionEventRepository.findByTransaction_IdInOrderByCreatedAtAsc(ids);
        return events.stream().collect(Collectors.groupingBy(event -> event.getTransaction().getId()));
    }

    private record TransactionAggregate(
            TransactionEntity transaction,
            List<TransactionEventEntity> events,
            TransactionStatus status,
            long amountSat,
            Long balanceAfterSat,
            String failureReason
    ) {

        static TransactionAggregate from(TransactionEntity transaction, List<TransactionEventEntity> events) {
            TransactionStatus status = TransactionStatus.PENDING;
            long amountSat = transaction.getAmount();
            Long balanceAfterSat = null;
            String failureReason = null;
            for (TransactionEventEntity event : events) {
                if (event.getEventType() == TransactionEventType.STATUS_CHANGED && event.getStatus() != null) {
                    status = event.getStatus();
                    if (event.getBalanceAfter() != null) {
                        balanceAfterSat = event.getBalanceAfter();
                    }
                    if (event.getDetails() != null) {
                        failureReason = event.getDetails();
                    }
                } else if (event.getEventType() == TransactionEventType.FEE_ADDED && event.getAmountDelta() != null) {
                    amountSat += event.getAmountDelta();
                }
            }
            return new TransactionAggregate(transaction, events, status, amountSat, balanceAfterSat, failureReason);
        }

        boolean isPending() {
            return status == TransactionStatus.PENDING;
        }
    }


    @Transactional
    public void processInternalTransfer(InternalTransferInitiatedEvent event) {
        TransactionEntity senderTx = transactionsRepository.findById(event.getTransactionId())
                .orElseThrow(() -> new AratiriException("Sender transaction not found for internal transfer."));

        TransactionProcessor debitProcessor = processors.get(TransactionType.LIGHTNING_DEBIT);
        TransactionAggregate senderAggregate = aggregateTransaction(senderTx);
        if (!senderAggregate.isPending()) {
            throw new AratiriException("Sender transaction is not pending and cannot be processed for internal transfer.");
        }
        long senderBalance = debitProcessor.process(senderTx);
        appendStatusEvent(senderTx, TransactionStatus.COMPLETED, senderBalance, null);

        CreateTransactionRequest creditRequest = new CreateTransactionRequest(
                event.getReceiverId(),
                event.getAmountSat(),
                TransactionCurrency.BTC,
                TransactionType.LIGHTNING_CREDIT,
                TransactionStatus.COMPLETED,
                "Internal transfer from: " + event.getSenderId(),
                event.getPaymentHash()
        );
        createAndSettleTransaction(creditRequest);
        LightningInvoiceEntity invoice = lightningInvoiceRepository.findByPaymentHash(event.getPaymentHash()).orElseThrow();

        try {
            CancelInvoiceMsg cancelInvoiceMsg = CancelInvoiceMsg.newBuilder()
                    .setPaymentHash(ByteString.copyFrom(HexFormat.of().parseHex(event.getPaymentHash())))
                    .build();
            invoiceBlockingStub.cancelInvoice(cancelInvoiceMsg);
            logger.info("Successfully canceled invoice on LND for internal transfer.");
        } catch (Exception e) {
            logger.error("Failed to cancel invoice on LND", e);
            throw new AratiriException("Failed to cancel invoice on LND: " + e.getMessage());
        }

        invoice.setInvoiceState(LightningInvoiceEntity.InvoiceState.SETTLED);
        invoice.setAmountPaidSats(event.getAmountSat());
        invoice.setSettledAt(LocalDateTime.now());
        lightningInvoiceRepository.save(invoice);
        InternalTransferCompletedEvent completedEvent = new InternalTransferCompletedEvent(
                event.getSenderId(),
                event.getReceiverId(),
                event.getAmountSat(),
                event.getPaymentHash(),
                LocalDateTime.now(),
                invoice.getMemo()
        );
        try {
            OutboxEventEntity outboxEvent = OutboxEventEntity.builder()
                    .aggregateType("INTERNAL_TRANSFER")
                    .aggregateId(event.getTransactionId())
                    .eventType(KafkaTopics.INTERNAL_TRANSFER_COMPLETED.getCode())
                    .payload(objectMapper.writeValueAsString(completedEvent))
                    .build();
            outboxEventRepository.save(outboxEvent);
        } catch (Exception e) {
            logger.error("Failed to create outbox event for InternalTransferCompletedEvent", e);
            throw new AratiriException("Failed to publish settlement event for internal transfer.");
        }

        logger.info("Successfully processed internal transfer for transactionId: {}", event.getTransactionId());
    }

    private TransactionEntity buildTransactionEntity(CreateTransactionRequest request) {
        TransactionEntity transaction = new TransactionEntity();
        transaction.setUserId(request.getUserId());
        transaction.setAmount(request.getAmountSat());
        transaction.setCurrency(request.getCurrency());
        transaction.setType(request.getType());
        transaction.setDescription(request.getDescription());
        transaction.setReferenceId(request.getReferenceId());
        return transaction;
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
    }
}