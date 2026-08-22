package com.aratiri.infrastructure.messaging.consumer;

import com.aratiri.infrastructure.messaging.KafkaTopicNames;
import com.aratiri.requests.application.PaymentRequestSagaService;
import com.aratiri.requests.application.event.PaymentRequestProvisionRequestedEvent;
import com.aratiri.requests.application.port.out.PaymentRequestPersistencePort;
import com.aratiri.requests.domain.PaymentRequest;
import com.aratiri.requests.domain.PaymentRequestStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;
import java.util.Set;

/**
 * Drives payment-request provisioning off the HTTP thread. The saga service's fenced claims make
 * redelivery idempotent; the {@code PaymentRequestSagaJob} lease-safe backstop preserves behavior
 * even when this consumer never runs. On exhaustion the record is dead-lettered and only logged.
 * The job remains the terminal-state authority (FAILED after 10 attempts).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentRequestProvisionConsumer {

    private static final Set<PaymentRequestStatus> TERMINAL_STATUSES = Set.of(
            PaymentRequestStatus.OPEN,
            PaymentRequestStatus.PAID,
            PaymentRequestStatus.CANCELLED,
            PaymentRequestStatus.FAILED
    );

    private final PaymentRequestSagaService sagaService;
    private final PaymentRequestPersistencePort persistencePort;
    private final JsonMapper jsonMapper;

    @KafkaListener(topics = KafkaTopicNames.PAYMENT_REQUEST_PROVISION_REQUESTED,
            groupId = "${aratiri.kafka.groups.payment-request-provision:payment-request-provision-group}")
    @RetryableTopic(
            attempts = "10",
            backOff = @BackOff(delay = 1000, multiplier = 2.0, maxDelay = 60000),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
            include = {Exception.class}
    )
    public void handleProvisionRequested(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        log.info("Provisioning payment request from topic: {}, partition: {}, offset: {}", topic, partition, offset);
        try {
            PaymentRequestProvisionRequestedEvent event = jsonMapper.readValue(message, PaymentRequestProvisionRequestedEvent.class);
            Optional<PaymentRequest> request = persistencePort.findById(event.requestId());
            if (request.isEmpty()) {
                log.warn("Payment request not found for provisioning event requestId={}; acknowledging", event.requestId());
                acknowledgment.acknowledge();
                return;
            }
            if (TERMINAL_STATUSES.contains(request.get().storedStatus())) {
                log.info("Payment request {} already in terminal state {}; skipping provisioning",
                        event.requestId(), request.get().storedStatus());
                acknowledgment.acknowledge();
                return;
            }
            sagaService.tryProvision(event.requestId());
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Payment request provisioning failed for message: {}", message, e);
            throw e;
        }
    }

    @DltHandler
    public void handleFailedProvisioning(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = "kafka_exception-message", required = false) String exceptionMessage) {
        log.error("Payment request provisioning dead-lettered after max retries. Topic: {}, Message: {}, Error: {}. " +
                "PaymentRequestSagaJob backstop continues to process the row.", topic, message, exceptionMessage);
    }
}
