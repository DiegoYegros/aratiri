package com.aratiri.aratiri.service.processor;

import com.aratiri.aratiri.entity.AccountEntity;
import com.aratiri.aratiri.entity.TransactionEntity;
import com.aratiri.aratiri.entity.TransactionType;
import com.aratiri.aratiri.exception.AratiriException;
import com.aratiri.aratiri.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
@RequiredArgsConstructor
public class InvoiceCreditProcessor implements TransactionProcessor {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final AccountRepository accountRepository;

    @Override
    public BigDecimal process(TransactionEntity transaction) {
        AccountEntity account = accountRepository.findByUserId(transaction.getUserId());
        if (account == null){
            throw new AratiriException("Account not found for user: " + transaction.getUserId());
        }
        BigDecimal amountInBTC = transaction.getAmount();
        BigDecimal satsInBtc = new BigDecimal(100_000_000);
        BigDecimal amountInSats = amountInBTC.multiply(satsInBtc);
        logger.info("AmountInBTC = {}, AmountInSats = {}", amountInBTC, amountInSats);
        long newBalance = account.getBalance() + amountInSats.longValue();
        account.setBalance(newBalance);
        accountRepository.save(account);
        BigDecimal btcValue = new BigDecimal(newBalance).divide(satsInBtc, 8, RoundingMode.HALF_UP);
        logger.info("Returning btcValue in invoiceCreditProcessor: [{}]", btcValue);
        return btcValue;
    }

    @Override
    public TransactionType supportedType() {
        return TransactionType.INVOICE_CREDIT;
    }
}