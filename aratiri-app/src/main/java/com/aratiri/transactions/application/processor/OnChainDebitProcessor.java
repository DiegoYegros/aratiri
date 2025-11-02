package com.aratiri.transactions.application.processor;

import com.aratiri.infrastructure.persistence.jpa.entity.AccountEntryType;
import com.aratiri.infrastructure.persistence.jpa.entity.TransactionEntity;
import com.aratiri.infrastructure.persistence.ledger.AccountLedgerService;
import com.aratiri.transactions.application.dto.TransactionType;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OnChainDebitProcessor implements TransactionProcessor {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final AccountLedgerService accountLedgerService;

    @Override
    public long process(TransactionEntity transaction) {
        long amountInSats = transaction.getAmount();
        logger.info("Debiting {} sats from account for on-chain transaction.", amountInSats);
        long delta = amountInSats * -1L;
        long newBalance = accountLedgerService.appendEntryForUser(
                transaction.getUserId(),
                transaction.getId(),
                delta,
                AccountEntryType.ONCHAIN_DEBIT,
                "On-chain withdrawal"
        );
        logger.info("New balance in sats: [{}]", newBalance);
        return newBalance;
    }

    @Override
    public TransactionType supportedType() {
        return TransactionType.ONCHAIN_DEBIT;
    }
}