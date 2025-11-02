package com.aratiri.payments.application.port.out;

import com.aratiri.transactions.application.dto.CreateTransactionRequest;
import com.aratiri.transactions.application.dto.TransactionDTOResponse;

public interface TransactionsPort {

    TransactionDTOResponse createTransaction(CreateTransactionRequest request);

    void confirmTransaction(String transactionId, String userId);

    void failTransaction(String transactionId, String reason);

    void addFeeToTransaction(String transactionId, long feeSat);
}
