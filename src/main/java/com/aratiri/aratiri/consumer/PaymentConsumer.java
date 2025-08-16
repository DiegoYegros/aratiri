package com.aratiri.aratiri.consumer;

import com.aratiri.aratiri.event.OnChainPaymentInitiatedEvent;
import com.aratiri.aratiri.event.PaymentInitiatedEvent;
import com.aratiri.aratiri.service.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentConsumer {

    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "payment.initiated", groupId = "payment-group")
    public void handlePaymentInitiated(String message, Acknowledgment acknowledgment) {
        try {
            PaymentInitiatedEvent event = objectMapper.readValue(message, PaymentInitiatedEvent.class);
            paymentService.initiateGrpcLightningPayment(event.getTransactionId(), event.getUserId(), event.getPayRequest());
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process payment initiated event: {}", message, e);
        }
    }

    @KafkaListener(topics = "onchain.payment.initiated", groupId = "payment-group")
    public void handleOnChainPaymentInitiated(String message, Acknowledgment acknowledgment) {
        try {
            OnChainPaymentInitiatedEvent event = objectMapper.readValue(message, OnChainPaymentInitiatedEvent.class);
            paymentService.initiateGrpcOnChainPayment(event.getTransactionId(), event.getUserId(), event.getPaymentRequest());
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process on-chain payment initiated event: {}", message, e);
        }
    }
}