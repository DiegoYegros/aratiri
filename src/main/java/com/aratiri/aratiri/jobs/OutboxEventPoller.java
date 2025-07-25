package com.aratiri.aratiri.jobs;

import com.aratiri.aratiri.entity.OutboxEventEntity;
import com.aratiri.aratiri.enums.KafkaTopics;
import com.aratiri.aratiri.event.PaymentInitiatedEvent;
import com.aratiri.aratiri.producer.InvoiceEventProducer;
import com.aratiri.aratiri.repository.OutboxEventRepository;
import com.aratiri.aratiri.service.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
public class OutboxEventPoller {

    private final OutboxEventRepository outboxEventRepository;
    private final InvoiceEventProducer invoiceEventProducer;
    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    public OutboxEventPoller(OutboxEventRepository outboxEventRepository, InvoiceEventProducer invoiceEventProducer, PaymentService paymentService, ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.invoiceEventProducer = invoiceEventProducer;
        this.paymentService = paymentService;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void processOutboxEvents() {
        List<OutboxEventEntity> pendingEvents = outboxEventRepository.findByProcessedAtIsNullOrderByCreatedAtAsc();
        if (pendingEvents.isEmpty()) {
            return;
        }
        log.info("Found {} pending events in outbox to process.", pendingEvents.size());
        for (OutboxEventEntity event : pendingEvents) {
            try {
                String eventType = event.getEventType();
                if (KafkaTopics.INVOICE_SETTLED.getCode().equals(eventType)) {
                    invoiceEventProducer.sendInvoiceSettledEventFromString(event.getPayload());
                } else if ("PAYMENT_INITIATED".equals(eventType)) {
                    PaymentInitiatedEvent eventPayload = objectMapper.readValue(event.getPayload(), PaymentInitiatedEvent.class);
                    paymentService.initiateGrpcPayment(eventPayload.getTransactionId(), eventPayload.getUserId(), eventPayload.getPayRequest());
                }
                event.setProcessedAt(Instant.now());
                outboxEventRepository.save(event);
            } catch (Exception e) {
                log.error("Error processing outbox event ID: {}. It will be retried.", event.getId(), e);
            }
        }
    }
}