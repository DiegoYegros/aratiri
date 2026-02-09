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
class OnChainCreditProcessorTest {

    @Mock
    private AccountLedgerService accountLedgerService;

    private OnChainCreditProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new OnChainCreditProcessor(accountLedgerService);
    }

    @Test
    void supportedType_shouldReturnOnChainCredit() {
        assertEquals(TransactionType.ONCHAIN_CREDIT, processor.supportedType());
    }

    @Test
    void process_shouldCreditLedger() {
        TransactionEntity tx = new TransactionEntity();
        tx.setId("tx-1");
        tx.setUserId("user-1");
        tx.setAmount(2500L);

        when(accountLedgerService.appendEntryForUser("user-1", "tx-1", 2500L,
                AccountEntryType.ONCHAIN_CREDIT, "On-chain deposit"))
                .thenReturn(12500L);

        long newBalance = processor.process(tx);

        assertEquals(12500L, newBalance);
        verify(accountLedgerService).appendEntryForUser("user-1", "tx-1", 2500L,
                AccountEntryType.ONCHAIN_CREDIT, "On-chain deposit");
    }
}
