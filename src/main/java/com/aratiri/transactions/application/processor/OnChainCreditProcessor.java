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
public class OnChainCreditProcessor implements TransactionProcessor {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final AccountLedgerService accountLedgerService;

    @Override
    public long process(TransactionEntity transaction) {
        long amountInSats = transaction.getAmount();
        logger.info("Crediting {} sats to account from on-chain transaction.", amountInSats);
        long delta = amountInSats;
        long newBalance = accountLedgerService.appendEntryForUser(
                transaction.getUserId(),
                transaction.getId(),
                delta,
                AccountEntryType.ONCHAIN_CREDIT,
                "On-chain deposit"
        );
        return newBalance;
    }

    @Override
    public TransactionType supportedType() {
        return TransactionType.ONCHAIN_CREDIT;
    }
}