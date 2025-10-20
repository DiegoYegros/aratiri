package com.aratiri.infrastructure.messaging.consumer;

import com.aratiri.event.InternalTransferInitiatedEvent;
import com.aratiri.transactions.application.port.in.TransactionsPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class InternalTransferConsumer {

    private final TransactionsPort transactionsService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "internal.transfer.initiated", groupId = "internal-transfer-group")
    public void handleInternalTransfer(String message, Acknowledgment acknowledgment) {
        try {
            InternalTransferInitiatedEvent event = objectMapper.readValue(message, InternalTransferInitiatedEvent.class);
            transactionsService.processInternalTransfer(event);
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process internal transfer: {}", message, e);
        }
    }
}