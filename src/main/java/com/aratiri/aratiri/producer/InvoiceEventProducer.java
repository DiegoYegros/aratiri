package com.aratiri.aratiri.producer;

import com.aratiri.aratiri.enums.KafkaTopics;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InvoiceEventProducer {

    private final Logger logger = LoggerFactory.getLogger(InvoiceEventProducer.class);
    private final KafkaTemplate<String, String> kafkaTemplate;

    public void sendInvoiceSettledEventFromString(String payload) {
        try {
            send(KafkaTopics.INVOICE_SETTLED, payload);
        } catch (Exception e) {
            logger.error("Failed to send event from outbox to Kafka.", e);
            throw e;
        }
    }

    public void sendInternalTransferEvent(String payload) {
        try {
            send(KafkaTopics.INTERNAL_TRANSFER_INITIATED, payload);
        } catch (Exception e) {
            logger.error("Failed to send event from outbox to Kafka.", e);
            throw e;
        }
    }

    public void sendInternalTransferCompletedEvent(String payload) {
        try {
            send(KafkaTopics.INTERNAL_TRANSFER_COMPLETED, payload);
        } catch (Exception e) {
            logger.error("Failed to send event to Kafka.", e);
            throw e;
        }
    }
    private void send(KafkaTopics topic, String payload) {
        kafkaTemplate.send(topic.getCode(), payload);
        logger.info("Successfully sent event from outbox to Kafka topic: {}", topic.getCode());
    }
}