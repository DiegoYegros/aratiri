package com.aratiri.accounts.application.port.out;

import com.aratiri.dto.transactions.TransactionDTOResponse;

import java.time.Instant;
import java.util.List;

public interface TransactionsPort {

    List<TransactionDTOResponse> getTransactions(Instant from, Instant to, String userId);
}
