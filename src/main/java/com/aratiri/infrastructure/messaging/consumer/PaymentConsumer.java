package com.aratiri.infrastructure.messaging.consumer;

import com.aratiri.infrastructure.messaging.KafkaTopicNames;
import com.aratiri.infrastructure.nodeoperations.LightningPaymentOperation;
import com.aratiri.infrastructure.nodeoperations.NodeOperationService;
import com.aratiri.infrastructure.nodeoperations.OnChainSendOperationFact;
import com.aratiri.payments.application.dto.OnChainPaymentDTOs;
import com.aratiri.payments.application.dto.PayInvoiceRequestDTO;
import com.aratiri.payments.application.event.OnChainPaymentInitiatedEvent;
import com.aratiri.payments.application.event.PaymentInitiatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentConsumer {

    private final NodeOperationService nodeOperationService;
    private final KafkaConsumerPolicy consumerPolicy;

    @KafkaListener(topics = KafkaTopicNames.PAYMENT_INITIATED, groupId = "payment-group")
    public void handlePaymentInitiated(String message, Acknowledgment acknowledgment) {
        consumerPolicy.deserializeHandleAndAcknowledge(
                message,
                PaymentInitiatedEvent.class,
                acknowledgment,
                "payment.initiated enqueue failed",
                event -> {
                    PayInvoiceRequestDTO payRequest = event.getPayRequest();

                    nodeOperationService.enqueueLightningPayment(LightningPaymentOperation.fromPaymentRequest(
                            event.getTransactionId(),
                            event.getUserId(),
                            payRequest
                    ));
                }
        );
    }

    @KafkaListener(topics = KafkaTopicNames.ONCHAIN_PAYMENT_INITIATED, groupId = "payment-group")
    public void handleOnChainPaymentInitiated(String message, Acknowledgment acknowledgment) {
        consumerPolicy.deserializeHandleAndAcknowledge(
                message,
                OnChainPaymentInitiatedEvent.class,
                acknowledgment,
                "onchain.payment.initiated enqueue failed",
                event -> {
                    OnChainPaymentDTOs.SendOnChainRequestDTO paymentRequest = event.getPaymentRequest();

                    nodeOperationService.enqueueOnChainSend(new OnChainSendOperationFact(
                            event.getTransactionId(),
                            event.getUserId(),
                            paymentRequest.getAddress(),
                            paymentRequest.getSatsAmount(),
                            paymentRequest.getSatPerVbyte(),
                            paymentRequest.getTargetConf(),
                            paymentRequest.getExternalReference(),
                            paymentRequest.getMetadata()
                    ));
                }
        );
    }

}
