package com.aratiri.aratiri.producer;

import com.aratiri.aratiri.entity.LightningInvoiceEntity;
import com.aratiri.aratiri.enums.KafkaTopics;
import com.aratiri.aratiri.event.InvoiceSettledEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class InvoiceEventProducer {

    private final Logger logger = LoggerFactory.getLogger(InvoiceEventProducer.class);
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void sendInvoiceSettledEvent(LightningInvoiceEntity invoice) {
        InvoiceSettledEvent event = new InvoiceSettledEvent(
                invoice.getUserId(),
                invoice.getAmountSats(),
                invoice.getPaymentHash(),
                LocalDateTime.now()
        );

        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(KafkaTopics.INVOICE_SETTLED.getCode(), payload);
        } catch (JsonProcessingException ex) {
            logger.error("Could not serialize InvoiceSettledEvent to JSON.", ex);
        }
    }
}