package com.aratiri.payments.infrastructure.transactions;

import com.aratiri.transactions.application.dto.CreateTransactionRequest;
import com.aratiri.transactions.application.dto.TransactionDTOResponse;
import com.aratiri.transactions.application.port.in.TransactionsPort;
import org.springframework.stereotype.Component;

@Component("paymentsTransactionsServiceAdapter")
public class TransactionsServiceAdapter implements com.aratiri.payments.application.port.out.TransactionsPort {

    private final TransactionsPort transactionsService;

    public TransactionsServiceAdapter(TransactionsPort transactionsService) {
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
