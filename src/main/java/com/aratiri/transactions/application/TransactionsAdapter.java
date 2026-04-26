package com.aratiri.transactions.application;

import com.aratiri.infrastructure.persistence.jpa.entity.*;
import com.aratiri.infrastructure.persistence.jpa.repository.TransactionsRepository;
import com.aratiri.shared.exception.AratiriException;
import com.aratiri.transactions.application.dto.*;
import com.aratiri.transactions.application.event.InternalTransferInitiatedEvent;
import com.aratiri.transactions.application.port.in.TransactionsPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;

@Service
public class TransactionsAdapter implements TransactionsPort {
    private static final String TRANSACTION_NOT_FOUND_FORMAT = "Transaction with id [%s] not found.";
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final TransactionsRepository transactionsRepository;
    private final TransactionSettlementService transactionSettlementService;
    private final TransactionSettlementModule transactionSettlementModule;

    public TransactionsAdapter(
            TransactionsRepository transactionsRepository,
            TransactionSettlementService transactionSettlementService,
            TransactionSettlementModule transactionSettlementModule
    ) {
        this.transactionsRepository = transactionsRepository;
        this.transactionSettlementService = transactionSettlementService;
        this.transactionSettlementModule = transactionSettlementModule;
    }

    @Override
    @Transactional
    public TransactionDTOResponse confirmTransaction(String id, String userId) {
        logger.info("In confirmTransaction, received id [{}]", id);
        TransactionEntity transaction = transactionsRepository.findById(id)
                .orElseThrow(() -> new AratiriException(String.format(TRANSACTION_NOT_FOUND_FORMAT, id)));

        if (!Objects.equals(transaction.getUserId(), userId)) {
            throw new AratiriException(String.format("Transaction [%s] does not correspond to current user.", id));
        }
        if (isExternalDebit(transaction)) {
            transactionSettlementModule.settleExternalDebit(new ExternalDebitCompletionSettlement(id, userId));
            return transactionsRepository.findById(id)
                    .map(this::mapToDtoFast)
                    .orElseThrow(() -> new AratiriException(String.format(TRANSACTION_NOT_FOUND_FORMAT, id)));
        }
        return mapToDto(transactionSettlementService.settlePending(transaction));
    }

    @Override
    @Transactional
    public TransactionDTOResponse confirmTransactionAsAdmin(String id) {
        logger.info("In confirmTransactionAsAdmin, received id [{}]", id);
        TransactionEntity transaction = transactionsRepository.findById(id)
                .orElseThrow(() -> new AratiriException(String.format(TRANSACTION_NOT_FOUND_FORMAT, id)));
        if (isExternalDebit(transaction)) {
            transactionSettlementModule.settleExternalDebit(new ExternalDebitCompletionSettlement(id, null));
            return transactionsRepository.findById(id)
                    .map(this::mapToDtoFast)
                    .orElseThrow(() -> new AratiriException(String.format(TRANSACTION_NOT_FOUND_FORMAT, id)));
        }
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
        TransactionEntity transaction = transactionsRepository.findById(transactionId)
                .orElseThrow(() -> new AratiriException(String.format("Transaction with id [%s] not found for failure.", transactionId)));
        if (isExternalDebit(transaction)) {
            transactionSettlementModule.failExternalDebit(new ExternalDebitFailureSettlement(transactionId, failureReason));
            return;
        }
        transactionSettlementService.failTransaction(transactionId, failureReason);
    }

    @Override
    @Transactional
    public void addFeeToTransaction(String transactionId, long feeSat) {
        transactionSettlementModule.applyLightningRoutingFee(new LightningRoutingFeeSettlement(transactionId, feeSat));
    }

    private boolean isExternalDebit(TransactionEntity transaction) {
        return transaction.getType() == TransactionType.LIGHTNING_DEBIT || transaction.getType() == TransactionType.ONCHAIN_DEBIT;
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
        transactionSettlementModule.settleInternalTransfer(new InternalTransferSettlement(
                event.getTransactionId(),
                event.getSenderId(),
                event.getReceiverId(),
                event.getAmountSat(),
                event.getPaymentHash()
        ));
    }
}
