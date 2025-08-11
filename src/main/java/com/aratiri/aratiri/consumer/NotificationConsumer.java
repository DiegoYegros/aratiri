package com.aratiri.aratiri.consumer;

import com.aratiri.aratiri.enums.KafkaTopics;
import com.aratiri.aratiri.event.InternalTransferCompletedEvent;
import com.aratiri.aratiri.event.InvoiceSettledEvent;
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

    @KafkaListener(topics = {"invoice.settled", "internal.transfer.completed"}, groupId = "notification-group")
    public void handlePaymentSettledForNotification(String message, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.info("In NotificationConsumer, received the topic [{}]", topic);
        try {
            String userId;
            long amount;
            String paymentHash;
            String memo;
            if (topic.equals(KafkaTopics.INVOICE_SETTLED.getCode())) {
                InvoiceSettledEvent event = objectMapper.readValue(message, InvoiceSettledEvent.class);
                userId = event.getUserId();
                amount = event.getAmount();
                paymentHash = event.getPaymentHash();
                memo = event.getMemo();
            } else {
                InternalTransferCompletedEvent event = objectMapper.readValue(message, InternalTransferCompletedEvent.class);
                userId = event.getReceiverId();
                amount = event.getAmountSat();
                paymentHash = event.getPaymentHash();
                memo = event.getMemo();
            }

            Map<String, Object> notificationPayload = Map.of(
                    "message", "Payment Received",
                    "amountSats", amount,
                    "paymentHash", paymentHash,
                    "memo", memo
            );
            notificationsService.sendNotification(userId, "payment_received", notificationPayload);
        } catch (Exception e) {
            log.error("Failed to process event for notification from topic {}: {}", topic, message, e);
        }
    }
}
