package com.aratiri.aratiri.jobs;

import com.aratiri.aratiri.entity.OutboxEventEntity;
import com.aratiri.aratiri.enums.KafkaTopics;
import com.aratiri.aratiri.event.InternalTransferInitiatedEvent;
import com.aratiri.aratiri.event.OnChainPaymentInitiatedEvent;
import com.aratiri.aratiri.event.PaymentInitiatedEvent;
import com.aratiri.aratiri.producer.InvoiceEventProducer;
import com.aratiri.aratiri.repository.OutboxEventRepository;
import com.aratiri.aratiri.service.PaymentService;
import com.aratiri.aratiri.service.TransactionsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventPoller {
    private final OutboxEventRepository outboxEventRepository;
    private final InvoiceEventProducer invoiceEventProducer;
    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;
    private final TransactionsService transactionsService;

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
                    paymentService.initiateGrpcLightningPayment(eventPayload.getTransactionId(), eventPayload.getUserId(), eventPayload.getPayRequest());
                } else if ("ONCHAIN_PAYMENT_INITIATED".equals(eventType)) {
                    OnChainPaymentInitiatedEvent eventPayload = objectMapper.readValue(event.getPayload(), OnChainPaymentInitiatedEvent.class);
                    paymentService.initiateGrpcOnChainPayment(eventPayload.getTransactionId(), eventPayload.getUserId(), eventPayload.getPaymentRequest());
                } else if ("INTERNAL_TRANSFER_INITIATED".equals(eventType)) {
                    invoiceEventProducer.sendInternalTransferEvent(event.getPayload());
                } else {
                    log.error("Couldn't find a way to process this event type: [{}] -- Ignoring.", eventType);
                    continue;
                }
                event.setProcessedAt(Instant.now());
                outboxEventRepository.save(event);
            } catch (Exception e) {
                log.error("Error processing outbox event ID: {}. It will be retried.", event.getId(), e);
            }
        }
    }
}