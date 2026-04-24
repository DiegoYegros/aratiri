package com.aratiri.infrastructure.messaging.consumer;

import com.aratiri.infrastructure.messaging.KafkaTopicNames;
import com.aratiri.payments.application.event.OnChainPaymentInitiatedEvent;
import com.aratiri.payments.application.event.PaymentInitiatedEvent;
import com.aratiri.payments.application.port.in.PaymentsPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentConsumer {

    private final PaymentsPort paymentsPort;
    private final JsonMapper jsonMapper;

    @KafkaListener(topics = KafkaTopicNames.PAYMENT_INITIATED, groupId = "payment-group")
    public void handlePaymentInitiated(String message, Acknowledgment acknowledgment) {
        try {
            PaymentInitiatedEvent event = jsonMapper.readValue(message, PaymentInitiatedEvent.class);
            paymentsPort.initiateGrpcLightningPayment(event.getTransactionId(), event.getUserId(), event.getPayRequest());
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process payment initiated event: {}", message, e);
            throw new IllegalStateException("payment.initiated processing failed", e);
        }
    }

    @KafkaListener(topics = KafkaTopicNames.ONCHAIN_PAYMENT_INITIATED, groupId = "payment-group")
    public void handleOnChainPaymentInitiated(String message, Acknowledgment acknowledgment) {
        try {
            OnChainPaymentInitiatedEvent event = jsonMapper.readValue(message, OnChainPaymentInitiatedEvent.class);
            paymentsPort.initiateGrpcOnChainPayment(event.getTransactionId(), event.getUserId(), event.getPaymentRequest());
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process on-chain payment initiated event: {}", message, e);
            throw new IllegalStateException("onchain.payment.initiated processing failed", e);
        }
    }
}