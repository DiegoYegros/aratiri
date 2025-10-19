package com.aratiri.transactions.application;

import com.aratiri.dto.transactions.TransactionDTOResponse;
import com.aratiri.service.TransactionsService;
import com.aratiri.transactions.application.port.in.TransactionsPort;
import org.springframework.stereotype.Service;

@Service
public class TransactionsAdapter implements TransactionsPort {

    private final TransactionsService transactionsService;

    public TransactionsAdapter(TransactionsService transactionsService) {
        this.transactionsService = transactionsService;
    }

    @Override
    public TransactionDTOResponse confirmTransaction(String transactionId, String userId) {
        return transactionsService.confirmTransaction(transactionId, userId);
    }
}
