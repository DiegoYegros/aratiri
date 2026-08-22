package com.aratiri.infrastructure.messaging.consumer;

import com.aratiri.auth.application.port.out.NotificationPort;
import com.aratiri.infrastructure.messaging.KafkaTopicNames;
import com.aratiri.invoices.application.event.InvoiceSettledEvent;
import com.aratiri.payments.application.event.PaymentSentEvent;
import com.aratiri.transactions.application.event.InternalTransferCompletedEvent;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class NotificationConsumer {

    private static final String MESSAGE_KEY = "message";
    private static final String AMOUNT_SATS_KEY = "amountSats";
    private static final String PAYMENT_REQUEST_KEY = "paymentRequest";
    private static final Duration DEFAULT_DEDUP_TTL = Duration.ofMinutes(5);

    private final NotificationPort notificationsService;
    private final JsonMapper jsonMapper;
    private final Cache<String, Boolean> deliveredEvents;

    @Autowired
    public NotificationConsumer(NotificationPort notificationsService, JsonMapper jsonMapper) {
        this(notificationsService, jsonMapper, DEFAULT_DEDUP_TTL);
    }

    NotificationConsumer(NotificationPort notificationsService, JsonMapper jsonMapper, Duration dedupTtl) {
        this.notificationsService = notificationsService;
        this.jsonMapper = jsonMapper;
        this.deliveredEvents = Caffeine.newBuilder()
                .expireAfterWrite(dedupTtl)
                .maximumSize(10_000)
                .build();
    }

    @KafkaListener(
            topics = {
                    KafkaTopicNames.INVOICE_SETTLED,
                    KafkaTopicNames.INTERNAL_TRANSFER_COMPLETED,
                    KafkaTopicNames.PAYMENT_SENT
            },
            groupId = "notification-group"
    )
    public void handlePaymentSettledForNotification(String message, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic, Acknowledgment ack) {
        log.info("In NotificationConsumer, received the topic [{}]", topic);
        try {
            Notification notification = parse(message, topic);
            if (notification == null) {
                log.warn("Unknown topic in NotificationConsumer: {}; acknowledging to avoid blocking the partition", topic);
                ack.acknowledge();
                return;
            }
            if (deliveredEvents.getIfPresent(notification.dedupKey()) != null) {
                log.info("Duplicate notification event {} already delivered within the dedup window; skipping",
                        notification.dedupKey());
                ack.acknowledge();
                return;
            }
            notificationsService.sendNotification(notification.userId(), notification.eventName(), notification.payload());
            if (notification.senderNotification() != null) {
                notificationsService.sendNotification(
                        notification.senderNotification().userId(),
                        notification.senderNotification().eventName(),
                        notification.senderNotification().payload());
            }
            deliveredEvents.put(notification.dedupKey(), Boolean.TRUE);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process event for notification from topic {}: {}", topic, message, e);
            throw new IllegalStateException("notification consumer processing failed for topic: " + topic, e);
        }
    }

    private Notification parse(String message, String topic) throws tools.jackson.core.JacksonException {
        if (topic.equals(KafkaTopicNames.INVOICE_SETTLED)) {
            InvoiceSettledEvent event = jsonMapper.readValue(message, InvoiceSettledEvent.class);
            return new Notification(
                    event.getUserId(),
                    "payment_received",
                    topic + ":" + event.getPaymentHash(),
                    Map.of(
                            MESSAGE_KEY, "Payment Received",
                            AMOUNT_SATS_KEY, event.getAmount(),
                            PAYMENT_REQUEST_KEY, event.getPaymentHash(),
                            "memo", event.getMemo()
                    ),
                    null
            );
        }
        if (topic.equals(KafkaTopicNames.INTERNAL_TRANSFER_COMPLETED)) {
            InternalTransferCompletedEvent event = jsonMapper.readValue(message, InternalTransferCompletedEvent.class);
            return new Notification(
                    event.getReceiverId(),
                    "payment_received",
                    topic + ":" + event.getPaymentHash(),
                    Map.of(
                            MESSAGE_KEY, "Payment Received",
                            AMOUNT_SATS_KEY, event.getAmountSat(),
                            PAYMENT_REQUEST_KEY, event.getPaymentHash(),
                            "memo", event.getMemo()
                    ),
                    new Notification(
                            event.getSenderId(),
                            "payment_sent",
                            null,
                            Map.of(
                                    MESSAGE_KEY, "Payment Sent",
                                    AMOUNT_SATS_KEY, event.getAmountSat(),
                                    PAYMENT_REQUEST_KEY, event.getPaymentHash(),
                                    "memo", event.getMemo()
                            ),
                            null
                    )
            );
        }
        if (topic.equals(KafkaTopicNames.PAYMENT_SENT)) {
            PaymentSentEvent event = jsonMapper.readValue(message, PaymentSentEvent.class);
            // On-chain payments legitimately have a null paymentHash; Map.of rejects nulls.
            Map<String, Object> payload = new HashMap<>();
            payload.put(MESSAGE_KEY, "Payment Sent");
            payload.put("transactionId", event.getTransactionId());
            payload.put(AMOUNT_SATS_KEY, event.getAmount());
            payload.put(PAYMENT_REQUEST_KEY, event.getPaymentHash());
            payload.put("memo", event.getMemo());
            return new Notification(
                    event.getUserId(),
                    "payment_sent",
                    topic + ":" + event.getTransactionId(),
                    payload,
                    null
            );
        }
        return null;
    }

    private record Notification(
            String userId,
            String eventName,
            String dedupKey,
            Map<String, Object> payload,
            Notification senderNotification
    ) {
    }
}
