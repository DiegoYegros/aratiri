package com.aratiri.transactions.application;

import com.aratiri.infrastructure.messaging.KafkaTopics;
import com.aratiri.infrastructure.persistence.jpa.entity.*;
import com.aratiri.infrastructure.persistence.jpa.repository.LightningInvoiceRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.OutboxEventRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.TransactionsRepository;
import com.aratiri.shared.exception.AratiriException;
import com.aratiri.transactions.application.dto.*;
import com.aratiri.transactions.application.event.InternalTransferCompletedEvent;
import com.aratiri.transactions.application.event.InternalTransferInitiatedEvent;
import com.aratiri.transactions.application.event.InternalInvoiceCancelEvent;
import com.aratiri.transactions.application.port.in.TransactionsPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;

@Service
public class TransactionsAdapter implements TransactionsPort {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final TransactionsRepository transactionsRepository;
    private final TransactionSettlementService transactionSettlementService;
    private final LightningInvoiceRepository lightningInvoiceRepository;
    private final JsonMapper jsonMapper;
    private final OutboxEventRepository outboxEventRepository;

    public TransactionsAdapter(TransactionsRepository transactionsRepository, TransactionSettlementService transactionSettlementService, LightningInvoiceRepository lightningInvoiceRepository, JsonMapper jsonMapper, OutboxEventRepository outboxEventRepository) {
        this.transactionsRepository = transactionsRepository;
        this.transactionSettlementService = transactionSettlementService;
        this.lightningInvoiceRepository = lightningInvoiceRepository;
        this.jsonMapper = jsonMapper;
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
        return mapToDto(transactionSettlementService.settlePending(transaction));
    }

    @Override
    @Transactional
    public TransactionDTOResponse confirmTransactionAsAdmin(String id) {
        logger.info("In confirmTransactionAsAdmin, received id [{}]", id);
        TransactionEntity transaction = transactionsRepository.findById(id)
                .orElseThrow(() -> new AratiriException(String.format("Transaction with id [%s] not found.", id)));
        return mapToDto(transactionSettlementService.settlePending(transaction));
    }

    @Override
    public boolean existsByReferenceId(String referenceId) {
        return transactionsRepository.existsByReferenceId(referenceId);
    }

    @Override
    @Transactional
    public TransactionDTOResponse createAndSettleTransaction(CreateTransactionRequest request) {
        logger.info("In createAndSettleTransaction. Received object [{}]", request);
        return mapToDto(transactionSettlementService.createAndSettleTransaction(request));
    }


    @Override
    @Transactional
    public TransactionDTOResponse createTransaction(CreateTransactionRequest request) {
        logger.info("In createTransaction. Received request to create transaction: [{}]", request);
        return mapToDto(transactionSettlementService.createTransaction(request));
    }

    @Override
    public List<TransactionDTOResponse> getTransactions(Instant from, Instant to, String userId) {
        List<TransactionEntity> transactions = transactionsRepository
                .findByUserIdAndCreatedAtBetween(userId, from, to);
        logger.info("Got a list of [{}] transactions from db", transactions.size());
        Map<String, List<TransactionEventEntity>> eventsByTransaction = transactionSettlementService.eventsByTransaction(transactions);
        return transactions.stream()
                .map(tx -> mapToDto(TransactionState.from(tx, eventsByTransaction.getOrDefault(tx.getId(), List.of()))))
                .toList();
    }

    @Override
    public Optional<TransactionDTOResponse> getTransactionById(String id, String userId) {
        return transactionsRepository.findById(id)
                .filter(tx -> Objects.equals(tx.getUserId(), userId))
                .map(this::mapToDtoFast);
    }

    @Override
    public TransactionPageResponse getTransactionsWithCursor(String userId, String cursor, int limit) {
        int cappedLimit = Math.clamp(limit, 1, 200);
        List<TransactionEntity> transactions;

        if (cursor != null && !cursor.isEmpty()) {
            String[] parts = cursor.split("_");
            Instant cursorCreatedAt = Instant.ofEpochMilli(Long.parseLong(parts[0]));
            String cursorId = parts[1];
            transactions = transactionsRepository.findByUserIdWithCursor(
                    userId, cursorCreatedAt, cursorId,
                    org.springframework.data.domain.PageRequest.of(0, cappedLimit + 1)
            );
        } else {
            transactions = transactionsRepository.findByUserIdOrderByCreatedAtDescIdDesc(
                    userId,
                    org.springframework.data.domain.PageRequest.of(0, cappedLimit + 1)
            );
        }

        boolean hasMore = transactions.size() > cappedLimit;
        if (hasMore) {
            transactions = transactions.subList(0, cappedLimit);
        }

        List<TransactionDTOResponse> dtos = transactions.stream()
                .map(this::mapToDtoFast)
                .toList();

        String nextCursor = null;
        if (hasMore && !transactions.isEmpty()) {
            TransactionEntity last = transactions.get(transactions.size() - 1);
            nextCursor = last.getCreatedAt().toEpochMilli() + "_" + last.getId();
        }

        return TransactionPageResponse.builder()
                .transactions(dtos)
                .nextCursor(nextCursor)
                .hasMore(hasMore)
                .build();
    }

    @Override
    @Transactional
    public void failTransaction(String transactionId, String failureReason) {
        transactionSettlementService.failTransaction(transactionId, failureReason);
    }

    @Override
    @Transactional
    public void addFeeToTransaction(String transactionId, long feeSat) {
        transactionSettlementService.addFeeToTransaction(transactionId, feeSat);
    }

    private TransactionDTOResponse mapToDto(TransactionState state) {
        TransactionEntity transaction = state.transaction();
        return TransactionDTOResponse.builder()
                .id(transaction.getId())
                .userId(transaction.getUserId())
                .createdAt(OffsetDateTime.from(transaction.getCreatedAt().atZone(ZoneId.systemDefault())))
                .amountSat(state.amountSat())
                .type(transaction.getType())
                .balanceAfterSat(state.balanceAfterSat())
                .description(transaction.getDescription())
                .failureReason(state.failureReason())
                .referenceId(transaction.getReferenceId())
                .externalReference(transaction.getExternalReference())
                .metadata(transaction.getMetadata())
                .status(state.status())
                .currency(transaction.getCurrency())
                .build();
    }

    private TransactionDTOResponse mapToDtoFast(TransactionEntity transaction) {
        return TransactionDTOResponse.builder()
                .id(transaction.getId())
                .userId(transaction.getUserId())
                .createdAt(OffsetDateTime.from(transaction.getCreatedAt().atZone(ZoneId.systemDefault())))
                .amountSat(transaction.getCurrentAmount())
                .type(transaction.getType())
                .balanceAfterSat(transaction.getBalanceAfter())
                .description(transaction.getDescription())
                .failureReason(transaction.getFailureReason())
                .referenceId(transaction.getReferenceId())
                .externalReference(transaction.getExternalReference())
                .metadata(transaction.getMetadata())
                .status(TransactionStatus.valueOf(transaction.getCurrentStatus()))
                .currency(transaction.getCurrency())
                .build();
    }

    @Transactional
    public void processInternalTransfer(InternalTransferInitiatedEvent event) {
        TransactionEntity senderTx = transactionsRepository.findById(event.getTransactionId())
                .orElseThrow(() -> new AratiriException("Sender transaction not found for internal transfer."));

        transactionSettlementService.settleInternalTransferDebit(senderTx);

        CreateTransactionRequest creditRequest = new CreateTransactionRequest(
                event.getReceiverId(),
                event.getAmountSat(),
                TransactionCurrency.BTC,
                TransactionType.LIGHTNING_CREDIT,
                TransactionStatus.COMPLETED,
                "Internal transfer from: " + event.getSenderId(),
                event.getPaymentHash(),
                null,
                null
        );
        transactionSettlementService.createAndSettleTransaction(creditRequest);
        LightningInvoiceEntity invoice = lightningInvoiceRepository.findByPaymentHash(event.getPaymentHash()).orElseThrow();

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
                    .payload(jsonMapper.writeValueAsString(completedEvent))
                    .build();
            outboxEventRepository.save(outboxEvent);
        } catch (Exception e) {
            logger.error("Failed to create outbox event for InternalTransferCompletedEvent", e);
            throw new AratiriException("Failed to publish settlement event for internal transfer.");
        }

        try {
            InternalInvoiceCancelEvent cancelEvent = new InternalInvoiceCancelEvent(event.getPaymentHash());
            OutboxEventEntity cancelOutboxEvent = OutboxEventEntity.builder()
                    .aggregateType("INTERNAL_INVOICE_CANCEL")
                    .aggregateId(event.getPaymentHash())
                    .eventType(KafkaTopics.INTERNAL_INVOICE_CANCEL.getCode())
                    .payload(jsonMapper.writeValueAsString(cancelEvent))
                    .build();
            outboxEventRepository.save(cancelOutboxEvent);
        } catch (Exception e) {
            logger.error("Failed to create outbox event for InternalInvoiceCancelEvent", e);
        }

        logger.info("Successfully processed internal transfer for transactionId: {}", event.getTransactionId());
    }
}
