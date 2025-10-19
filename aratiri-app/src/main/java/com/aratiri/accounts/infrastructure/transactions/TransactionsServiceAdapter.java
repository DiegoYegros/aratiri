package com.aratiri.accounts.infrastructure.transactions;

import com.aratiri.accounts.application.port.out.TransactionsPort;
import com.aratiri.dto.transactions.TransactionDTOResponse;
import com.aratiri.service.TransactionsService;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component("accountsTransactionsServiceAdapter")
public class TransactionsServiceAdapter implements TransactionsPort {

    private final TransactionsService transactionsService;

    public TransactionsServiceAdapter(TransactionsService transactionsService) {
        this.transactionsService = transactionsService;
    }

    @Override
    public List<TransactionDTOResponse> getTransactions(Instant from, Instant to, String userId) {
        return transactionsService.getTransactions(from, to, userId);
    }
}
