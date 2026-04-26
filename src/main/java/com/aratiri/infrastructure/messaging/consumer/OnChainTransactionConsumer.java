package com.aratiri.infrastructure.messaging.consumer;

import com.aratiri.infrastructure.messaging.KafkaTopicNames;
import com.aratiri.transactions.application.OnChainCreditSettlement;
import com.aratiri.transactions.application.TransactionSettlementModule;
import com.aratiri.transactions.application.event.OnChainTransactionReceivedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
@Slf4j
public class OnChainTransactionConsumer {

    private final TransactionSettlementModule transactionSettlementModule;
    private final JsonMapper jsonMapper;

    @KafkaListener(topics = KafkaTopicNames.ONCHAIN_TRANSACTION_RECEIVED, groupId = "transaction-group")
    public void handleOnChainTransactionReceived(String message, Acknowledgment acknowledgment) {
        try {
            OnChainTransactionReceivedEvent event = jsonMapper.readValue(message, OnChainTransactionReceivedEvent.class);
            OnChainCreditSettlement settlement = new OnChainCreditSettlement(
                    event.getUserId(),
                    event.getAmount(),
                    event.getTxHash(),
                    event.getOutputIndex()
            );
            transactionSettlementModule.settleOnChainCredit(settlement);
            acknowledgment.acknowledge();
        } catch (DataIntegrityViolationException e) {
            log.warn("Data integrity violation for transaction. Processed by another instance. Acknowledging message. Error: {}", e.getMessage());
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process on-chain transaction received event: {}", message, e);
            throw new IllegalStateException("onchain.transaction.received processing failed", e);
        }
    }
}
