package com.aratiri.payments.application.port.out;

import com.aratiri.dto.transactions.CreateTransactionRequest;
import com.aratiri.dto.transactions.TransactionDTOResponse;

public interface TransactionsPort {

    TransactionDTOResponse createTransaction(CreateTransactionRequest request);

    void confirmTransaction(String transactionId, String userId);

    void failTransaction(String transactionId, String reason);
}
