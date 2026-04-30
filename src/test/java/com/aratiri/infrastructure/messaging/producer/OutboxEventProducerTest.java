package com.aratiri.infrastructure.messaging.producer;

import com.aratiri.infrastructure.messaging.KafkaTopics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxEventProducerTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private OutboxEventProducer producer;

    @BeforeEach
    void setUp() {
        producer = new OutboxEventProducer(kafkaTemplate);
    }

    @Test
    void sendEvent_sendsToKafka() {
        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(KafkaTopics.PAYMENT_INITIATED.getCode(), "payload"))
                .thenReturn(future);

        assertDoesNotThrow(() -> producer.sendEvent(KafkaTopics.PAYMENT_INITIATED, "payload"));
    }

    @Test
    void sendEvent_throwsOnExecutionException() {
        CompletableFuture<SendResult<String, String>> failingFuture = CompletableFuture.failedFuture(new ExecutionException(new RuntimeException("kafka error")));
        when(kafkaTemplate.send(anyString(), anyString())).thenReturn(failingFuture);

        assertThrows(IllegalStateException.class,
                () -> producer.sendEvent(KafkaTopics.PAYMENT_INITIATED, "payload"));
    }

    @Test
    void sendEvent_throwsOnTimeoutException() {
        CompletableFuture<SendResult<String, String>> timeoutFuture = CompletableFuture.failedFuture(new TimeoutException("timeout"));
        when(kafkaTemplate.send(anyString(), anyString())).thenReturn(timeoutFuture);

        assertThrows(IllegalStateException.class,
                () -> producer.sendEvent(KafkaTopics.PAYMENT_INITIATED, "payload"));
    }

    @Test
    void sendEvent_throwsOnInterruptedException() {
        CompletableFuture<SendResult<String, String>> interruptedFuture = new CompletableFuture<>();
        interruptedFuture.completeExceptionally(new InterruptedException("interrupted"));
        when(kafkaTemplate.send(anyString(), anyString())).thenReturn(interruptedFuture);

        assertThrows(IllegalStateException.class,
                () -> producer.sendEvent(KafkaTopics.PAYMENT_INITIATED, "payload"));
    }
}
