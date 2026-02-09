package com.aratiri.infrastructure.messaging.consumer;

import com.aratiri.transactions.application.event.InternalTransferInitiatedEvent;
import com.aratiri.transactions.application.port.in.TransactionsPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
@Slf4j
public class InternalTransferConsumer {

    private final TransactionsPort transactionsService;
    private final JsonMapper jsonMapper;

    @KafkaListener(topics = "internal.transfer.initiated", groupId = "internal-transfer-group")
    public void handleInternalTransfer(String message, Acknowledgment acknowledgment) {
        try {
            InternalTransferInitiatedEvent event = jsonMapper.readValue(message, InternalTransferInitiatedEvent.class);
            log.info("Processing internal transfer event. txId={}, senderId={}, receiverId={}, amountSat={}",
                    event.getTransactionId(), event.getSenderId(), event.getReceiverId(), event.getAmountSat());
            transactionsService.processInternalTransfer(event);
            acknowledgment.acknowledge();
            log.info("Internal transfer processed and acknowledged. txId={}", event.getTransactionId());
        } catch (Exception e) {
            log.error("Failed to process internal transfer: {}", message, e);
            throw new RuntimeException("Internal transfer processing failed", e);
        }
    }
}
