package com.aratiri.transactions.application.port.in;

import com.aratiri.dto.transactions.TransactionDTOResponse;

public interface TransactionsPort {

    TransactionDTOResponse confirmTransaction(String transactionId, String userId);
}
