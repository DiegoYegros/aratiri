package com.aratiri.aratiri.consumer;

import com.aratiri.aratiri.enums.KafkaTopics;
import com.aratiri.aratiri.event.InternalTransferCompletedEvent;
import com.aratiri.aratiri.event.InvoiceSettledEvent;
import com.aratiri.aratiri.event.PaymentSentEvent;
import com.aratiri.aratiri.service.NotificationsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationConsumer {

    private final NotificationsService notificationsService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = {"invoice.settled", "internal.transfer.completed", "payment.sent"}, groupId = "notification-group")
    public void handlePaymentSettledForNotification(String message, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.info("In NotificationConsumer, received the topic [{}]", topic);
        try {
            String userId;
            String eventName;
            Map<String, Object> notificationPayload;

            if (topic.equals(KafkaTopics.INVOICE_SETTLED.getCode())) {
                InvoiceSettledEvent event = objectMapper.readValue(message, InvoiceSettledEvent.class);
                userId = event.getUserId();
                eventName = "payment_received";
                notificationPayload = Map.of(
                        "message", "Payment Received",
                        "amountSats", event.getAmount(),
                        "paymentHash", event.getPaymentHash(),
                        "memo", event.getMemo()
                );
            } else if (topic.equals(KafkaTopics.INTERNAL_TRANSFER_COMPLETED.getCode())) {
                InternalTransferCompletedEvent event = objectMapper.readValue(message, InternalTransferCompletedEvent.class);
                userId = event.getReceiverId();
                eventName = "payment_received";
                notificationPayload = Map.of(
                        "message", "Payment Received",
                        "amountSats", event.getAmountSat(),
                        "paymentHash", event.getPaymentHash(),
                        "memo", event.getMemo()
                );
                notificationsService.sendNotification(event.getSenderId(), "payment_sent", "dummy_payload");
            } else if (topic.equals(KafkaTopics.PAYMENT_SENT.getCode())) {
                PaymentSentEvent event = objectMapper.readValue(message, PaymentSentEvent.class);
                userId = event.getUserId();
                eventName = "payment_sent";
                notificationPayload = Map.of(
                        "message", "Payment Sent",
                        "transactionId", event.getTransactionId(),
                        "amountSats", event.getAmount(),
                        "paymentHash", event.getPaymentHash(),
                        "memo", event.getMemo()
                );
            } else {
                log.warn("Unknown topic in NotificationConsumer: {}", topic);
                return;
            }

            notificationsService.sendNotification(userId, eventName, notificationPayload);
        } catch (Exception e) {
            log.error("Failed to process event for notification from topic {}: {}", topic, message, e);
        }
    }
}