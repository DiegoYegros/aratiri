package com.aratiri.transactions.application.processor;

import com.aratiri.infrastructure.persistence.jpa.entity.TransactionEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.AccountEntryType;
import com.aratiri.infrastructure.persistence.ledger.AccountLedgerService;
import com.aratiri.shared.constants.BitcoinConstants;
import com.aratiri.transactions.application.dto.TransactionType;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class InvoiceCreditProcessor implements TransactionProcessor {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final AccountLedgerService accountLedgerService;

    @Override
    public BigDecimal process(TransactionEntity transaction) {
        BigDecimal amountInBTC = transaction.getAmount();
        BigDecimal amountInSats = amountInBTC.multiply(BitcoinConstants.SATOSHIS_PER_BTC);
        logger.info("AmountInBTC = {}, AmountInSats = {}", amountInBTC, amountInSats);
        long delta = amountInSats.longValueExact();
        long newBalance = accountLedgerService.appendEntryForUser(
                transaction.getUserId(),
                transaction.getId(),
                delta,
                AccountEntryType.LIGHTNING_CREDIT,
                "Lightning invoice settled"
        );
        return BitcoinConstants.satoshisToBtc(newBalance);
    }

    @Override
    public TransactionType supportedType() {
        return TransactionType.LIGHTNING_CREDIT;
    }
}