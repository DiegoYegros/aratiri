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
            kafkaTemplate.send(KafkaTopics.INVOICE_SETTLED.getCode(), payload);
            logger.info("Successfully sent event from outbox to Kafka topic: {}", KafkaTopics.INVOICE_SETTLED.getCode());
        } catch (Exception e) {
            logger.error("Failed to send event from outbox to Kafka.", e);
            throw e;
        }
    }
}