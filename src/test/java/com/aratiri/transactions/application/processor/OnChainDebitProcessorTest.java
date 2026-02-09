package com.aratiri.transactions.application.processor;

import com.aratiri.infrastructure.persistence.jpa.entity.AccountEntryType;
import com.aratiri.infrastructure.persistence.jpa.entity.TransactionEntity;
import com.aratiri.infrastructure.persistence.ledger.AccountLedgerService;
import com.aratiri.transactions.application.dto.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OnChainDebitProcessorTest {

    @Mock
    private AccountLedgerService accountLedgerService;

    private OnChainDebitProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new OnChainDebitProcessor(accountLedgerService);
    }

    @Test
    void supportedType_shouldReturnOnChainDebit() {
        assertEquals(TransactionType.ONCHAIN_DEBIT, processor.supportedType());
    }

    @Test
    void process_shouldDebitLedger() {
        TransactionEntity tx = new TransactionEntity();
        tx.setId("tx-1");
        tx.setUserId("user-1");
        tx.setAmount(2000L);

        when(accountLedgerService.appendEntryForUser("user-1", "tx-1", -2000L,
                AccountEntryType.ONCHAIN_DEBIT, "On-chain withdrawal"))
                .thenReturn(8000L);

        long newBalance = processor.process(tx);

        assertEquals(8000L, newBalance);
        verify(accountLedgerService).appendEntryForUser("user-1", "tx-1", -2000L,
                AccountEntryType.ONCHAIN_DEBIT, "On-chain withdrawal");
    }
}
