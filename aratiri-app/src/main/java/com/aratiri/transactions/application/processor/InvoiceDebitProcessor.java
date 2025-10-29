package com.aratiri.transactions.application.processor;

import com.aratiri.infrastructure.persistence.jpa.entity.AccountEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.TransactionEntity;
import com.aratiri.infrastructure.persistence.jpa.repository.AccountRepository;
import com.aratiri.shared.constants.BitcoinConstants;
import com.aratiri.shared.exception.AratiriException;
import com.aratiri.transactions.application.dto.TransactionType;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class InvoiceDebitProcessor implements TransactionProcessor {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final AccountRepository accountRepository;

    @Override
    public BigDecimal process(TransactionEntity transaction) {
        AccountEntity account = accountRepository.findByUserId(transaction.getUserId());
        if (account == null) {
            throw new AratiriException("Account not found for user: " + transaction.getUserId());
        }

        BigDecimal amountInBTC = transaction.getAmount();
        BigDecimal amountInSats = BitcoinConstants.btcToSatoshis(amountInBTC);
        logger.info("Debiting {} sats from account.", amountInSats);
        long newBalance = account.getBalance() - amountInSats.longValue();
        if (newBalance < 0) {
            throw new AratiriException("Insufficient funds for transaction settlement.");
        }
        account.setBalance(newBalance);
        accountRepository.save(account);
        BigDecimal btcValue = BitcoinConstants.satoshisToBtc(newBalance);
        logger.info("Returning new balance in BTC: [{}]", btcValue);
        return btcValue;
    }

    @Override
    public TransactionType supportedType() {
        return TransactionType.LIGHTNING_DEBIT;
    }
}