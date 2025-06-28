package com.aratiri.aratiri.service;

import com.aratiri.aratiri.dto.transactions.CreateTransactionRequest;
import com.aratiri.aratiri.dto.transactions.TransactionDTOResponse;

public interface TransactionsService {
    TransactionDTOResponse confirmTransaction(String id, String userId);
    TransactionDTOResponse createAndSettleTransaction(CreateTransactionRequest request);
    }
