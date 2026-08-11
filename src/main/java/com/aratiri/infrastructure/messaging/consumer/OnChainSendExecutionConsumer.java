package com.aratiri.infrastructure.messaging.consumer;

import com.aratiri.infrastructure.messaging.KafkaTopicNames;
import com.aratiri.payments.application.event.OnChainPaymentInitiatedEvent;
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

@Component
@RequiredArgsConstructor
@Slf4j
public class OnChainSendExecutionConsumer {

    private final PaymentExecutionService paymentExecutionService;
    private final JsonMapper jsonMapper;

    @KafkaListener(topics = KafkaTopicNames.ONCHAIN_PAYMENT_INITIATED,
            groupId = "${aratiri.kafka.groups.onchain-send-execution:onchain-send-execution-group}")
    @RetryableTopic(
            attempts = "5",
            backOff = @BackOff(delay = 1000, multiplier = 2.0),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
            include = {Exception.class}
    )
    public void handleOnChainPaymentInitiated(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        log.info("Executing on-chain send from topic: {}, partition: {}, offset: {}", topic, partition, offset);
        try {
            OnChainPaymentInitiatedEvent event = jsonMapper.readValue(message, OnChainPaymentInitiatedEvent.class);
            paymentExecutionService.executeOnChainSend(event);
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("On-chain send execution failed for message: {}", message, e);
            throw e;
        }
    }

    @DltHandler
    public void handleFailedOnChainSendExecution(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = "kafka_exception-message", required = false) String exceptionMessage,
            @Header(value = "retry_topic-attempts", required = false) Object deliveryAttemptHeader) {
        log.error("On-chain send execution failed after max attempts. Topic: {}, Message: {}, Error: {}",
                topic, message, exceptionMessage);
        try {
            OnChainPaymentInitiatedEvent event = jsonMapper.readValue(message, OnChainPaymentInitiatedEvent.class);
            paymentExecutionService.handleOnChainUnknownOutcome(
                    event, exceptionMessage, LightningPaymentExecutionConsumer.resolveAttemptCount(deliveryAttemptHeader));
        } catch (Exception e) {
            log.error("Could not process dead-lettered on-chain send execution: {}", message, e);
        }
    }
}
