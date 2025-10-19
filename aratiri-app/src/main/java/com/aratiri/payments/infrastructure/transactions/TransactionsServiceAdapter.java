package com.aratiri.payments.infrastructure.transactions;

import com.aratiri.dto.transactions.CreateTransactionRequest;
import com.aratiri.dto.transactions.TransactionDTOResponse;
import com.aratiri.payments.application.port.out.TransactionsPort;
import com.aratiri.service.TransactionsService;
import org.springframework.stereotype.Component;

@Component("paymentsTransactionsServiceAdapter")
public class TransactionsServiceAdapter implements TransactionsPort {

    private final TransactionsService transactionsService;

    public TransactionsServiceAdapter(TransactionsService transactionsService) {
        this.transactionsService = transactionsService;
    }

    @Override
    public TransactionDTOResponse createTransaction(CreateTransactionRequest request) {
        return transactionsService.createTransaction(request);
    }

    @Override
    public void confirmTransaction(String transactionId, String userId) {
        transactionsService.confirmTransaction(transactionId, userId);
    }

    @Override
    public void failTransaction(String transactionId, String reason) {
        transactionsService.failTransaction(transactionId, reason);
    }
}
