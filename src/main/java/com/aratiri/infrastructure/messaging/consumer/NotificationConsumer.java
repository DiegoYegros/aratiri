package com.aratiri.infrastructure.messaging.consumer;

import com.aratiri.auth.application.port.out.NotificationPort;
import com.aratiri.infrastructure.messaging.KafkaTopics;
import com.aratiri.invoices.application.event.InvoiceSettledEvent;
import com.aratiri.payments.application.event.PaymentSentEvent;
import com.aratiri.transactions.application.event.InternalTransferCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationConsumer {

    private static final String MESSAGE_KEY = "message";
    private static final String AMOUNT_SATS_KEY = "amountSats";
    private static final String PAYMENT_REQUEST_KEY = "paymentRequest";

    private final NotificationPort notificationsService;
    private final JsonMapper jsonMapper;

    @KafkaListener(topics = {"invoice.settled", "internal.transfer.completed", "payment.sent"}, groupId = "notification-group")
    public void handlePaymentSettledForNotification(String message, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic, Acknowledgment ack) {
        log.info("In NotificationConsumer, received the topic [{}]", topic);
        try {
            String userId;
            String eventName;
            Map<String, Object> notificationPayload;

            if (topic.equals(KafkaTopics.INVOICE_SETTLED.getCode())) {
                InvoiceSettledEvent event = jsonMapper.readValue(message, InvoiceSettledEvent.class);
                userId = event.getUserId();
                eventName = "payment_received";
                notificationPayload = Map.of(
                        MESSAGE_KEY, "Payment Received",
                        AMOUNT_SATS_KEY, event.getAmount(),
                        PAYMENT_REQUEST_KEY, event.getPaymentHash(),
                        "memo", event.getMemo()
                );
            } else if (topic.equals(KafkaTopics.INTERNAL_TRANSFER_COMPLETED.getCode())) {
                InternalTransferCompletedEvent event = jsonMapper.readValue(message, InternalTransferCompletedEvent.class);
                userId = event.getReceiverId();
                eventName = "payment_received";
                notificationPayload = Map.of(
                        MESSAGE_KEY, "Payment Received",
                        AMOUNT_SATS_KEY, event.getAmountSat(),
                        PAYMENT_REQUEST_KEY, event.getPaymentHash(),
                        "memo", event.getMemo()
                );
                Map<String, Object> senderPayload = Map.of(
                        MESSAGE_KEY, "Payment Sent",
                        AMOUNT_SATS_KEY, event.getAmountSat(),
                        PAYMENT_REQUEST_KEY, event.getPaymentHash(),
                        "memo", event.getMemo()
                );
                notificationsService.sendNotification(event.getSenderId(), "payment_sent", senderPayload);
            } else if (topic.equals(KafkaTopics.PAYMENT_SENT.getCode())) {
                PaymentSentEvent event = jsonMapper.readValue(message, PaymentSentEvent.class);
                userId = event.getUserId();
                eventName = "payment_sent";
                notificationPayload = Map.of(
                        MESSAGE_KEY, "Payment Sent",
                        "transactionId", event.getTransactionId(),
                        AMOUNT_SATS_KEY, event.getAmount(),
                        PAYMENT_REQUEST_KEY, event.getPaymentHash(),
                        "memo", event.getMemo()
                );
            } else {
                log.warn("Unknown topic in NotificationConsumer: {}", topic);
                return;
            }
            notificationsService.sendNotification(userId, eventName, notificationPayload);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process event for notification from topic {}: {}", topic, message, e);
        }
    }
}
