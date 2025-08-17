package com.aratiri.aratiri.consumer;

import com.aratiri.aratiri.constant.BitcoinConstants;
import com.aratiri.aratiri.dto.transactions.CreateTransactionRequest;
import com.aratiri.aratiri.dto.transactions.TransactionCurrency;
import com.aratiri.aratiri.dto.transactions.TransactionStatus;
import com.aratiri.aratiri.dto.transactions.TransactionType;
import com.aratiri.aratiri.event.OnChainTransactionReceivedEvent;
import com.aratiri.aratiri.service.TransactionsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OnChainTransactionConsumer {

    private final TransactionsService transactionsService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "onchain.transaction.received", groupId = "transaction-group")
    public void handleOnChainTransactionReceived(String message, Acknowledgment acknowledgment) {
        try {
            OnChainTransactionReceivedEvent event = objectMapper.readValue(message, OnChainTransactionReceivedEvent.class);
            if (transactionsService.existsByReferenceId(event.getTxHash())) {
                log.warn("Transaction with reference ID {} already processed. Skipping.", event.getTxHash());
                acknowledgment.acknowledge();
                return;
            }
            CreateTransactionRequest creditRequest = new CreateTransactionRequest(
                    event.getUserId(),
                    BitcoinConstants.satoshisToBtc(event.getAmount()),
                    TransactionCurrency.BTC,
                    TransactionType.ONCHAIN_CREDIT,
                    TransactionStatus.COMPLETED,
                    "On-chain payment received",
                    event.getTxHash()
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