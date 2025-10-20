package com.aratiri.transactions.application.port.in;

import com.aratiri.transactions.application.dto.CreateTransactionRequest;
import com.aratiri.transactions.application.dto.TransactionDTOResponse;
import com.aratiri.transactions.application.event.InternalTransferInitiatedEvent;

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
