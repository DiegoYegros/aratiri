package com.aratiri.transactions.application.processor;

import com.aratiri.shared.constants.BitcoinConstants;
import com.aratiri.transactions.application.dto.TransactionType;
import com.aratiri.infrastructure.persistence.jpa.entity.AccountEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.TransactionEntity;
import com.aratiri.shared.exception.AratiriException;
import com.aratiri.infrastructure.persistence.jpa.repository.AccountRepository;
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
        return TransactionType.LIGHTNING_CREDIT;
    }
}