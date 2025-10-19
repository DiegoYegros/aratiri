package com.aratiri.service.processor;

import com.aratiri.core.constants.BitcoinConstants;
import com.aratiri.dto.transactions.TransactionType;
import com.aratiri.entity.AccountEntity;
import com.aratiri.entity.TransactionEntity;
import com.aratiri.core.exception.AratiriException;
import com.aratiri.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class OnChainCreditProcessor implements TransactionProcessor {
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
        logger.info("Crediting {} sats to account from on-chain transaction.", amountInSats);
        long newBalance = account.getBalance() + amountInSats.longValue();
        account.setBalance(newBalance);
        accountRepository.save(account);
        return BitcoinConstants.satoshisToBtc(newBalance);
    }

    @Override
    public TransactionType supportedType() {
        return TransactionType.ONCHAIN_CREDIT;
    }
}