package com.aratiri.aratiri.service;

import com.aratiri.aratiri.dto.transactions.CreateTransactionRequest;
import com.aratiri.aratiri.dto.transactions.TransactionDTOResponse;

import java.time.Instant;
import java.util.List;

public interface TransactionsService {
    TransactionDTOResponse confirmTransaction(String id, String userId);

    TransactionDTOResponse createAndSettleTransaction(CreateTransactionRequest request);
    TransactionDTOResponse createTransaction(CreateTransactionRequest request);
    List<TransactionDTOResponse> getTransactions(Instant from, Instant to, String userId);
    void failTransaction(String transactionId, String failureReason);
}
