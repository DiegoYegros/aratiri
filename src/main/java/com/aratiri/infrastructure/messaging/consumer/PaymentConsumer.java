package com.aratiri.infrastructure.messaging.consumer;

import com.aratiri.infrastructure.messaging.KafkaTopicNames;
import com.aratiri.infrastructure.nodeoperations.NodeOperationService;
import com.aratiri.payments.application.dto.OnChainPaymentDTOs;
import com.aratiri.payments.application.dto.PayInvoiceRequestDTO;
import com.aratiri.payments.application.event.OnChainPaymentInitiatedEvent;
import com.aratiri.payments.application.event.PaymentInitiatedEvent;
import com.aratiri.payments.application.port.out.InvoicesPort;
import com.aratiri.payments.domain.DecodedInvoice;
import com.aratiri.payments.infrastructure.json.JsonUtils;
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

    private final NodeOperationService nodeOperationService;
    private final InvoicesPort invoicesPort;
    private final JsonMapper jsonMapper;

    @KafkaListener(topics = KafkaTopicNames.PAYMENT_INITIATED, groupId = "payment-group")
    public void handlePaymentInitiated(String message, Acknowledgment acknowledgment) {
        try {
            PaymentInitiatedEvent event = jsonMapper.readValue(message, PaymentInitiatedEvent.class);
            PayInvoiceRequestDTO payRequest = event.getPayRequest();

            String paymentHash = extractPaymentHash(payRequest.getInvoice(), event.getTransactionId());

            nodeOperationService.enqueueLightningPayment(
                    event.getTransactionId(),
                    event.getUserId(),
                    paymentHash,
                    JsonUtils.toJson(payRequest)
            );

            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Failed to enqueue lightning payment operation: {}", message, e);
            throw new IllegalStateException("payment.initiated enqueue failed", e);
        }
    }

    @KafkaListener(topics = KafkaTopicNames.ONCHAIN_PAYMENT_INITIATED, groupId = "payment-group")
    public void handleOnChainPaymentInitiated(String message, Acknowledgment acknowledgment) {
        try {
            OnChainPaymentInitiatedEvent event = jsonMapper.readValue(message, OnChainPaymentInitiatedEvent.class);
            OnChainPaymentDTOs.SendOnChainRequestDTO paymentRequest = event.getPaymentRequest();

            nodeOperationService.enqueueOnChainSend(
                    event.getTransactionId(),
                    event.getUserId(),
                    JsonUtils.toJson(paymentRequest)
            );

            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Failed to enqueue on-chain payment operation: {}", message, e);
            throw new IllegalStateException("onchain.payment.initiated enqueue failed", e);
        }
    }

    private String extractPaymentHash(String invoice, String transactionId) {
        try {
            DecodedInvoice decoded = invoicesPort.decodeInvoice(invoice);
            return decoded.paymentHash();
        } catch (Exception e) {
            log.warn("Failed to decode invoice for transactionId: {}, enqueueing without payment hash", transactionId, e);
            return null;
        }
    }
}
