package com.aratiri.aratiri.service.impl;

import com.aratiri.aratiri.dto.transactions.CreateTransactionRequest;
import com.aratiri.aratiri.dto.transactions.TransactionDTOResponse;
import com.aratiri.aratiri.entity.TransactionEntity;
import com.aratiri.aratiri.enums.TransactionStatus;
import com.aratiri.aratiri.enums.TransactionType;
import com.aratiri.aratiri.exception.AratiriException;
import com.aratiri.aratiri.repository.TransactionsRepository;
import com.aratiri.aratiri.service.TransactionsService;
import com.aratiri.aratiri.service.processor.TransactionProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TransactionsServiceImpl implements TransactionsService {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final TransactionsRepository transactionsRepository;
    private final Map<TransactionType, TransactionProcessor> processors;
    private static final Set<TransactionType> SETTLEABLE_TYPES = Set.of(
            TransactionType.INVOICE_CREDIT,
            TransactionType.ONCHAIN_DEPOSIT
    );

    public TransactionsServiceImpl(TransactionsRepository transactionsRepository, List<TransactionProcessor> processorList) {
        this.transactionsRepository = transactionsRepository;
        this.processors = processorList.stream()
                .collect(Collectors.toMap(TransactionProcessor::supportedType, Function.identity()));
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
                    HttpStatus.BAD_REQUEST
            );
        }
        TransactionEntity transaction = new TransactionEntity();
        transaction.setUserId(request.getUserId());
        transaction.setAmount(request.getAmount());
        transaction.setCurrency(request.getCurrency());
        transaction.setType(request.getType());
        transaction.setDescription(request.getDescription());
        transaction.setReferenceId(request.getReferenceId());
        transaction.setStatus(TransactionStatus.COMPLETED);
        TransactionProcessor processor = processors.get(transaction.getType());
        if (processor == null) {
            throw new AratiriException("No processor configured for type: " + transaction.getType());
        }
        BigDecimal newBalance = processor.process(transaction);
        transaction.setBalanceAfter(newBalance);
        TransactionEntity savedTransaction = transactionsRepository.save(transaction);
        logger.info("Saved the transaction with new state. This is the saved transaction: [{}]", savedTransaction);
        return mapToDto(savedTransaction);
    }


    @Override
    @Transactional
    public TransactionDTOResponse createTransaction(CreateTransactionRequest request) {
        logger.info("In createTransaction. Received request to create transaction: [{}]", request);
        TransactionEntity transaction = new TransactionEntity();
        transaction.setUserId(request.getUserId());
        transaction.setAmount(request.getAmount());
        transaction.setCurrency(request.getCurrency());
        transaction.setType(request.getType());
        transaction.setDescription(request.getDescription());
        transaction.setReferenceId(request.getReferenceId());
        transaction.setStatus(request.getStatus());
        TransactionEntity savedTransaction = transactionsRepository.save(transaction);
        logger.info("Successfully created new transaction record with status [{}]. Transaction: [{}]",
                savedTransaction.getStatus(), savedTransaction);
        return mapToDto(savedTransaction);
    }

    @Override
    public List<TransactionDTOResponse> getTransactions(Instant from, Instant to, String userId) {
        List<TransactionEntity> transactions = transactionsRepository
                .findByUserIdAndCreatedAtBetween(userId, from, to);
        logger.info("Got this list of transactions from db: [{}]", transactions);
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
                .referenceId(savedTransaction.getReferenceId())
                .status(savedTransaction.getStatus())
                .currency(savedTransaction.getCurrency())
                .build();
    }
}
