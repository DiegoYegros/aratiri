package com.aratiri.aratiri.service.processor;

import com.aratiri.aratiri.constants.BitcoinConstants;
import com.aratiri.aratiri.dto.transactions.TransactionType;
import com.aratiri.aratiri.entity.AccountEntity;
import com.aratiri.aratiri.entity.TransactionEntity;
import com.aratiri.aratiri.exception.AratiriException;
import com.aratiri.aratiri.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class OnChainDebitProcessor implements TransactionProcessor {

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
        logger.info("Debiting {} sats from account for on-chain transaction.", amountInSats);

        long newBalance = account.getBalance() - amountInSats.longValue();
        if (newBalance < 0) {
            throw new AratiriException("Insufficient funds for transaction settlement.");
        }
        account.setBalance(newBalance);
        accountRepository.save(account);
        BigDecimal newBalanceInBtc = BitcoinConstants.satoshisToBtc(newBalance);
        logger.info("New balance in BTC: [{}]", newBalanceInBtc);
        return newBalanceInBtc;
    }

    @Override
    public TransactionType supportedType() {
        return TransactionType.ONCHAIN_DEBIT;
    }
}
