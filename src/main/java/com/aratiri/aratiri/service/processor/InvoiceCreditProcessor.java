package com.aratiri.aratiri.service.processor;

import com.aratiri.aratiri.entity.AccountEntity;
import com.aratiri.aratiri.entity.TransactionEntity;
import com.aratiri.aratiri.entity.TransactionType;
import com.aratiri.aratiri.exception.AratiriException;
import com.aratiri.aratiri.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class InvoiceCreditProcessor implements TransactionProcessor {

    private final AccountRepository accountRepository;

    @Override
    public BigDecimal process(TransactionEntity transaction) {
        AccountEntity account = accountRepository.findByUserId(transaction.getUserId());
        if (account == null){
            throw new AratiriException("Account not found for user: " + transaction.getUserId());
        }
        long newBalance = account.getBalance() + Long.parseLong(transaction.getAmount().toPlainString());
        account.setBalance(newBalance);
        accountRepository.save(account);
        return new BigDecimal(newBalance);
    }

    @Override
    public TransactionType supportedType() {
        return TransactionType.INVOICE_CREDIT;
    }
}