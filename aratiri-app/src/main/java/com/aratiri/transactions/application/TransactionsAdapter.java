package com.aratiri.transactions.application;

import com.aratiri.infrastructure.messaging.KafkaTopics;
import com.aratiri.infrastructure.persistence.jpa.entity.LightningInvoiceEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.OutboxEventEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.TransactionEntity;
import com.aratiri.infrastructure.persistence.jpa.repository.LightningInvoiceRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.OutboxEventRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.TransactionsRepository;
import com.aratiri.shared.constants.BitcoinConstants;
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

import java.math.BigDecimal;
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

    public TransactionsAdapter(TransactionsRepository transactionsRepository, List<TransactionProcessor> processorList, LightningInvoiceRepository lightningInvoiceRepository, ObjectMapper objectMapper, InvoicesGrpc.InvoicesBlockingStub invoiceStub, OutboxEventRepository outboxEventRepository) {
        this.transactionsRepository = transactionsRepository;
        this.processors = processorList.stream()
                .collect(Collectors.toMap(TransactionProcessor::supportedType, Function.identity()));
        this.lightningInvoiceRepository = lightningInvoiceRepository;
        this.objectMapper = objectMapper;
        this.invoiceBlockingStub = invoiceStub;
        this.outboxEventRepository = outboxEventRepository;
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
        if (!TransactionStatus.PENDING.equals(transaction.getStatus())) {
            throw new AratiriException(String.format("Transaction status [%s] is not valid for confirmation.", transaction.getStatus()));
        }
        TransactionProcessor processor = processors.get(transaction.getType());
        if (processor == null) {
            throw new IllegalStateException("No processor configured for type: " + transaction.getType());
        }
        BigDecimal newBalance = processor.process(transaction);
        transaction.setBalanceAfter(newBalance);
        transaction.setStatus(TransactionStatus.COMPLETED);
        TransactionEntity savedTransaction = transactionsRepository.save(transaction);
        logger.info("Saved the transaction with new state. This is the saved transaction: [{}]", savedTransaction);
        return mapToDto(savedTransaction);
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
        TransactionEntity transaction = buildTransactionEntity(request, TransactionStatus.COMPLETED);
        TransactionProcessor processor = processors.get(transaction.getType());
        if (processor == null) {
            throw new AratiriException("No processor configured for type: " + transaction.getType());
        }
        BigDecimal newBalance = processor.process(transaction);
        transaction.setBalanceAfter(newBalance);
        TransactionEntity savedTransaction = transactionsRepository.save(transaction);
        logger.info("Created and settled transaction. This is the saved transaction: [{}]", savedTransaction);
        return mapToDto(savedTransaction);
    }


    @Override
    @Transactional
    public TransactionDTOResponse createTransaction(CreateTransactionRequest request) {
        logger.info("In createTransaction. Received request to create transaction: [{}]", request);
        TransactionEntity transaction = buildTransactionEntity(request, request.getStatus());
        TransactionEntity savedTransaction = transactionsRepository.save(transaction);
        logger.info("Successfully created new transaction record with status [{}]. Transaction: [{}]",
                savedTransaction.getStatus(), savedTransaction);
        return mapToDto(savedTransaction);
    }

    @Override
    public List<TransactionDTOResponse> getTransactions(Instant from, Instant to, String userId) {
        List<TransactionEntity> transactions = transactionsRepository
                .findByUserIdAndCreatedAtBetween(userId, from, to);
        logger.info("Got a list of [{}] transactions from db", transactions.size());
        return transactions.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void failTransaction(String transactionId, String failureReason) {
        logger.warn("Failing transaction [{}]. Reason: {}", transactionId, failureReason);
        TransactionEntity transaction = transactionsRepository.findById(transactionId)
                .orElseThrow(() -> new AratiriException(String.format("Transaction with id [%s] not found for failure.", transactionId)));
        if (!TransactionStatus.PENDING.equals(transaction.getStatus())) {
            logger.error("Attempted to fail a transaction that was not PENDING. ID: {}, Current Status: {}",
                    transactionId, transaction.getStatus());
            throw new AratiriException(String.format("Transaction status [%s] is not valid for failure.", transaction.getStatus()));
        }
        transaction.setStatus(TransactionStatus.FAILED);
        transaction.setFailureReason(failureReason);
        transactionsRepository.save(transaction);
        logger.info("Transaction [{}] has been marked as FAILED.", transactionId);
    }

    private TransactionDTOResponse mapToDto(TransactionEntity savedTransaction) {
        return TransactionDTOResponse.builder().id(savedTransaction.getId())
                .createdAt(OffsetDateTime.from(savedTransaction.getCreatedAt().atZone(ZoneId.systemDefault())))
                .amount(savedTransaction.getAmount())
                .type(savedTransaction.getType())
                .balanceAfter(savedTransaction.getBalanceAfter())
                .description(savedTransaction.getDescription())
                .failureReason(savedTransaction.getFailureReason())
                .referenceId(savedTransaction.getReferenceId())
                .status(savedTransaction.getStatus())
                .currency(savedTransaction.getCurrency())
                .build();
    }


    @Transactional
    public void processInternalTransfer(InternalTransferInitiatedEvent event) {
        TransactionEntity senderTx = transactionsRepository.findById(event.getTransactionId())
                .orElseThrow(() -> new AratiriException("Sender transaction not found for internal transfer."));

        TransactionProcessor debitProcessor = processors.get(TransactionType.LIGHTNING_DEBIT);
        debitProcessor.process(senderTx);
        senderTx.setStatus(TransactionStatus.COMPLETED);
        transactionsRepository.save(senderTx);

        CreateTransactionRequest creditRequest = new CreateTransactionRequest(
                event.getReceiverId(),
                BitcoinConstants.satoshisToBtc(event.getAmountSat()),
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

    private TransactionEntity buildTransactionEntity(CreateTransactionRequest request, TransactionStatus transactionStatus) {
        TransactionEntity transaction = new TransactionEntity();
        transaction.setUserId(request.getUserId());
        transaction.setAmount(request.getAmount());
        transaction.setCurrency(request.getCurrency());
        transaction.setType(request.getType());
        transaction.setDescription(request.getDescription());
        transaction.setReferenceId(request.getReferenceId());
        transaction.setStatus(transactionStatus);
        return transaction;
    }
}