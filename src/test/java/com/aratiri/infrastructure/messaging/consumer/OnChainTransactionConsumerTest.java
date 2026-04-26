package com.aratiri.infrastructure.messaging.consumer;

import com.aratiri.transactions.application.OnChainCreditSettlement;
import com.aratiri.transactions.application.TransactionSettlementModule;
import com.aratiri.transactions.application.event.OnChainTransactionReceivedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OnChainTransactionConsumerTest {

    @Mock
    private TransactionSettlementModule transactionSettlementModule;

    @Mock
    private JsonMapper jsonMapper;

    @Mock
    private Acknowledgment acknowledgment;

    private OnChainTransactionConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new OnChainTransactionConsumer(transactionSettlementModule, jsonMapper);
    }

    @Test
    void handleOnChainTransactionReceived_settlesOnChainCreditThroughSettlementModule() throws Exception {
        String message = "{\"userId\":\"user-1\",\"amount\":3500,\"txHash\":\"tx-hash\",\"outputIndex\":2}";
        when(jsonMapper.readValue(message, OnChainTransactionReceivedEvent.class))
                .thenReturn(new OnChainTransactionReceivedEvent("user-1", 3500L, "tx-hash", 2L));

        consumer.handleOnChainTransactionReceived(message, acknowledgment);

        ArgumentCaptor<OnChainCreditSettlement> captor = ArgumentCaptor.forClass(OnChainCreditSettlement.class);
        verify(transactionSettlementModule).settleOnChainCredit(captor.capture());
        assertEquals("user-1", captor.getValue().userId());
        assertEquals(3500L, captor.getValue().amountSat());
        assertEquals("tx-hash", captor.getValue().txHash());
        assertEquals(2L, captor.getValue().outputIndex());
        assertEquals("tx-hash:2", captor.getValue().referenceId());
        verify(acknowledgment).acknowledge();
    }
}
