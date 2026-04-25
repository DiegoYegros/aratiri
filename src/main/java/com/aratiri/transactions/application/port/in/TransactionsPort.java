package com.aratiri.transactions.application.port.in;

import com.aratiri.transactions.application.dto.CreateTransactionRequest;
import com.aratiri.transactions.application.dto.TransactionDTOResponse;
import com.aratiri.transactions.application.dto.TransactionPageResponse;
import com.aratiri.transactions.application.event.InternalTransferInitiatedEvent;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TransactionsPort {
    TransactionDTOResponse confirmTransaction(String id, String userId);

    TransactionDTOResponse confirmTransactionAsAdmin(String id);

    boolean existsByReferenceId(String referenceId);

    TransactionDTOResponse createAndSettleTransaction(CreateTransactionRequest request);

    TransactionDTOResponse createTransaction(CreateTransactionRequest request);

    List<TransactionDTOResponse> getTransactions(Instant from, Instant to, String userId);

    Optional<TransactionDTOResponse> getTransactionById(String id, String userId);

    TransactionPageResponse getTransactionsWithCursor(String userId, String cursor, int limit);

    void failTransaction(String transactionId, String failureReason);

    void processInternalTransfer(InternalTransferInitiatedEvent eventPayload);

    void addFeeToTransaction(String transactionId, long feeSat);
}
