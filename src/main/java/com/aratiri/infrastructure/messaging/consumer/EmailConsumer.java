package com.aratiri.infrastructure.messaging.consumer;

import com.aratiri.auth.application.event.EmailRequestedEvent;
import com.aratiri.auth.application.event.EmailRequestedEvent.EmailType;
import com.aratiri.auth.application.port.out.EmailNotificationPort;
import com.aratiri.infrastructure.messaging.KafkaTopicNames;
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
public class EmailConsumer {

    private final EmailNotificationPort emailNotificationPort;
    private final JsonMapper jsonMapper;

    @KafkaListener(topics = KafkaTopicNames.EMAIL_REQUESTED,
            groupId = "${aratiri.kafka.groups.email:email-group}")
    @RetryableTopic(
            attempts = "5",
            backOff = @BackOff(delay = 2000, multiplier = 2.0),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
            include = {Exception.class}
    )
    public void handleEmailRequested(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        log.info("Sending email from topic: {}, partition: {}, offset: {}", topic, partition, offset);
        try {
            EmailRequestedEvent event = jsonMapper.readValue(message, EmailRequestedEvent.class);
            if (event.emailType() == EmailType.PASSWORD_RESET) {
                emailNotificationPort.sendPasswordResetEmail(event.to(), event.code());
            } else {
                emailNotificationPort.sendVerificationEmail(event.to(), event.code());
            }
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Email delivery failed for message: {}", message, e);
            throw e;
        }
    }

    @DltHandler
    public void handleFailedEmail(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = "kafka_exception-message", required = false) String exceptionMessage) {
        log.error("Email delivery dead-lettered after max retries. Topic: {}, Message: {}, Error: {}",
                topic, message, exceptionMessage);
    }
}
