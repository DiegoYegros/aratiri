package com.aratiri.infrastructure.messaging.consumer;

import com.aratiri.transactions.application.dto.CreateTransactionRequest;
import com.aratiri.transactions.application.dto.TransactionCurrency;
import com.aratiri.transactions.application.dto.TransactionStatus;
import com.aratiri.transactions.application.dto.TransactionType;
import com.aratiri.transactions.application.event.OnChainTransactionReceivedEvent;
import com.aratiri.transactions.application.port.in.TransactionsPort;
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

    private final TransactionsPort transactionsService;
    private final JsonMapper jsonMapper;

    @KafkaListener(topics = "onchain.transaction.received", groupId = "transaction-group")
    public void handleOnChainTransactionReceived(String message, Acknowledgment acknowledgment) {
        try {
            OnChainTransactionReceivedEvent event = jsonMapper.readValue(message, OnChainTransactionReceivedEvent.class);
            String referenceId = event.getTxHash() + ":" + event.getOutputIndex();
            if (transactionsService.existsByReferenceId(referenceId)) {
                log.warn("Transaction with reference ID {} already processed. Skipping.", referenceId);
                acknowledgment.acknowledge();
                return;
            }
            CreateTransactionRequest creditRequest = new CreateTransactionRequest(
                    event.getUserId(),
                    event.getAmount(),
                    TransactionCurrency.BTC,
                    TransactionType.ONCHAIN_CREDIT,
                    TransactionStatus.COMPLETED,
                    "On-chain payment received",
                    referenceId
            );
            transactionsService.createAndSettleTransaction(creditRequest);
            acknowledgment.acknowledge();
        } catch (DataIntegrityViolationException e) {
            log.warn("Data integrity violation for transaction. Processed by another instance. Acknowledging message. Error: {}", e.getMessage());
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process on-chain transaction received event: {}", message, e);
        }
    }
}