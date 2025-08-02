package com.aratiri.aratiri.service.processor;

import com.aratiri.aratiri.constants.BitcoinConstants;
import com.aratiri.aratiri.entity.AccountEntity;
import com.aratiri.aratiri.entity.TransactionEntity;
import com.aratiri.aratiri.dto.transactions.TransactionType;
import com.aratiri.aratiri.exception.AratiriException;
import com.aratiri.aratiri.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class InvoiceCreditProcessor implements TransactionProcessor {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final AccountRepository accountRepository;

    @Override
    public BigDecimal process(TransactionEntity transaction) {
        AccountEntity account = accountRepository.findByUserId(transaction.getUserId());
        if (account == null) {
            throw new AratiriException("Account not found for user: " + transaction.getUserId());
        }
        BigDecimal amountInBTC = transaction.getAmount();
        BigDecimal amountInSats = amountInBTC.multiply(BitcoinConstants.SATOSHIS_PER_BTC);
        logger.info("AmountInBTC = {}, AmountInSats = {}", amountInBTC, amountInSats);
        long newBalance = account.getBalance() + amountInSats.longValue();
        account.setBalance(newBalance);
        accountRepository.save(account);
        return BitcoinConstants.satoshisToBtc(newBalance);
    }

    @Override
    public TransactionType supportedType() {
        return TransactionType.INVOICE_CREDIT;
    }
}