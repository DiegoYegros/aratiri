package com.aratiri.infrastructure.messaging.consumer;

import com.aratiri.payments.application.event.OnChainPaymentInitiatedEvent;
import com.aratiri.payments.application.event.PaymentInitiatedEvent;
import com.aratiri.payments.application.port.in.PaymentsPort;
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

    private final PaymentsPort paymentsPort;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "payment.initiated", groupId = "payment-group")
    public void handlePaymentInitiated(String message, Acknowledgment acknowledgment) {
        try {
            PaymentInitiatedEvent event = objectMapper.readValue(message, PaymentInitiatedEvent.class);
            paymentsPort.initiateGrpcLightningPayment(event.getTransactionId(), event.getUserId(), event.getPayRequest());
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process payment initiated event: {}", message, e);
        }
    }

    @KafkaListener(topics = "onchain.payment.initiated", groupId = "payment-group")
    public void handleOnChainPaymentInitiated(String message, Acknowledgment acknowledgment) {
        try {
            OnChainPaymentInitiatedEvent event = objectMapper.readValue(message, OnChainPaymentInitiatedEvent.class);
            paymentsPort.initiateGrpcOnChainPayment(event.getTransactionId(), event.getUserId(), event.getPaymentRequest());
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process on-chain payment initiated event: {}", message, e);
        }
    }
}