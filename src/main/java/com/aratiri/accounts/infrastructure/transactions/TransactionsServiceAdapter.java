package com.aratiri.accounts.infrastructure.transactions;

import com.aratiri.transactions.application.dto.TransactionDTOResponse;
import com.aratiri.transactions.application.port.in.TransactionsPort;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component("accountsTransactionsServiceAdapter")
public class TransactionsServiceAdapter implements com.aratiri.accounts.application.port.out.TransactionsPort {

    private final TransactionsPort transactionsService;

    public TransactionsServiceAdapter(TransactionsPort transactionsService) {
        this.transactionsService = transactionsService;
    }

    @Override
    public List<TransactionDTOResponse> getTransactions(Instant from, Instant to, String userId) {
        return transactionsService.getTransactions(from, to, userId);
    }
}
