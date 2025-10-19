package com.aratiri.transactions.application.port.in;

import com.aratiri.dto.transactions.CreateTransactionRequest;
import com.aratiri.dto.transactions.TransactionDTOResponse;
import com.aratiri.event.InternalTransferInitiatedEvent;

import java.time.Instant;
import java.util.List;

public interface TransactionsPort {
    TransactionDTOResponse confirmTransaction(String id, String userId);

    boolean existsByReferenceId(String referenceId);

    TransactionDTOResponse createAndSettleTransaction(CreateTransactionRequest request);

    TransactionDTOResponse createTransaction(CreateTransactionRequest request);

    List<TransactionDTOResponse> getTransactions(Instant from, Instant to, String userId);

    void failTransaction(String transactionId, String failureReason);

    void processInternalTransfer(InternalTransferInitiatedEvent eventPayload);
}
