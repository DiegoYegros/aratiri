package com.aratiri.infrastructure.messaging;

import com.aratiri.AbstractIntegrationTest;
import com.aratiri.auth.application.port.out.EmailNotificationPort;
import com.aratiri.accounts.application.port.out.CurrencyConversionPort;
import com.aratiri.accounts.application.port.out.LightningAddressPort;
import com.aratiri.infrastructure.messaging.listener.LightningListener;
import com.aratiri.infrastructure.messaging.listener.OnChainTransactionListener;
import com.aratiri.infrastructure.messaging.consumer.NotificationConsumer;
import com.aratiri.infrastructure.messaging.producer.OutboxEventProducer;
import com.aratiri.infrastructure.persistence.jpa.entity.OutboxEventEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.OutboxPublishStatus;
import com.aratiri.infrastructure.persistence.jpa.repository.OutboxEventRepository;
import com.aratiri.infrastructure.scheduling.job.OutboxEventJob;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OutboxKafkaIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean
    private EmailNotificationPort emailNotificationPort;

    @MockitoBean
    private CurrencyConversionPort currencyConversionPort;

    @MockitoBean
    private LightningAddressPort lightningAddressPort;

    @MockitoBean
    private LightningListener lightningListener;

    @MockitoBean
    private OnChainTransactionListener onChainTransactionListener;

    @MockitoBean
    private NotificationConsumer notificationConsumer;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OutboxEventJob outboxEventJob;

    @Autowired
    private OutboxEventProducer outboxEventProducer;

    @Autowired
    private ConsumerFactory<String, String> consumerFactory;

    @Test
    @DisplayName("Outbox event published to Kafka topic")
    void outbox_event_published_to_kafka() {
        String topic = KafkaTopics.PAYMENT_SENT.getCode();
        String payload = "{\"userId\":\"test-user\",\"amount\":1000}";

        try (Consumer<String, String> consumer = createConsumer(topic)) {
            ConsumerRecords<String, String> records = pollFromEndAfter(consumer, () ->
                    outboxEventProducer.sendEvent(KafkaTopics.PAYMENT_SENT, payload)
            );

            assertFalse(records.isEmpty(), "Should have received at least one record");

            boolean payloadReceived = false;
            for (ConsumerRecord<String, String> consumerRecord : records.records(topic)) {
                if (payload.equals(consumerRecord.value())) {
                    payloadReceived = true;
                    break;
                }
            }
            assertTrue(payloadReceived, "Should have received the event payload published by this test");
        }
    }

    @Test
    @DisplayName("Outbox job processes pending events and marks them as processed")
    void outbox_job_processes_pending_events() {
        OutboxEventEntity event = OutboxEventEntity.builder()
                .aggregateType("PAYMENT")
                .aggregateId("payment-001")
                .eventType(KafkaTopics.PAYMENT_SENT.getCode())
                .payload("{\"userId\":\"test-user\",\"amount\":500}")
                .build();

        outboxEventRepository.save(event);
        assertNull(event.getProcessedAt());

        outboxEventJob.processOutboxEvents();

        OutboxEventEntity processed = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertNotNull(processed.getProcessedAt());
    }

    @Test
    @DisplayName("Outbox job does not reprocess already processed events")
    void outbox_job_does_not_reprocess_processed_events() {
        OutboxEventEntity event = OutboxEventEntity.builder()
                .aggregateType("PAYMENT")
                .aggregateId("payment-002")
                .eventType(KafkaTopics.PAYMENT_SENT.getCode())
                .payload("{\"userId\":\"test-user\",\"amount\":300}")
                .build();

        outboxEventRepository.save(event);

        outboxEventJob.processOutboxEvents();

        OutboxEventEntity processed = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertNotNull(processed.getProcessedAt());

        outboxEventJob.processOutboxEvents();

        OutboxEventEntity stillProcessed = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertEquals(processed.getProcessedAt(), stillProcessed.getProcessedAt());
    }

    @Test
    @DisplayName("Unknown event type is marked invalid and not marked processed")
    void unknown_event_type_marked_invalid() {
        OutboxEventEntity event = OutboxEventEntity.builder()
                .aggregateType("UNKNOWN")
                .aggregateId("unknown-001")
                .eventType("unknown.event.type")
                .payload("{\"data\":\"test\"}")
                .build();

        outboxEventRepository.save(event);

        outboxEventJob.processOutboxEvents();

        OutboxEventEntity unprocessed = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertNull(unprocessed.getProcessedAt());
        assertEquals(OutboxPublishStatus.INVALID, unprocessed.getPublishStatus());
        assertTrue(unprocessed.getLastError().contains("unknown.event.type"));
    }

    @Test
    @DisplayName("Multiple pending events are processed in order")
    void multiple_pending_events_processed_in_order() {
        OutboxEventEntity event1 = OutboxEventEntity.builder()
                .aggregateType("PAYMENT")
                .aggregateId("payment-003")
                .eventType(KafkaTopics.PAYMENT_SENT.getCode())
                .payload("{\"order\":1}")
                .build();

        OutboxEventEntity event2 = OutboxEventEntity.builder()
                .aggregateType("PAYMENT")
                .aggregateId("payment-004")
                .eventType(KafkaTopics.PAYMENT_SENT.getCode())
                .payload("{\"order\":2}")
                .build();

        outboxEventRepository.save(event1);
        outboxEventRepository.save(event2);

        outboxEventJob.processOutboxEvents();

        OutboxEventEntity processed1 = outboxEventRepository.findById(event1.getId()).orElseThrow();
        OutboxEventEntity processed2 = outboxEventRepository.findById(event2.getId()).orElseThrow();

        assertNotNull(processed1.getProcessedAt());
        assertNotNull(processed2.getProcessedAt());
        assertTrue(processed1.getProcessedAt().isBefore(processed2.getProcessedAt()) ||
                   processed1.getProcessedAt().equals(processed2.getProcessedAt()));
    }

    private Consumer<String, String> createConsumer(String topic) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, consumerFactory.getConfigurationProperties().get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG));
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        Consumer<String, String> consumer = new org.apache.kafka.clients.consumer.KafkaConsumer<>(props);
        consumer.subscribe(Collections.singletonList(topic));
        return consumer;
    }

    private ConsumerRecords<String, String> pollFromEndAfter(
            Consumer<String, String> consumer,
            Runnable publish
    ) {
        while (consumer.assignment().isEmpty()) {
            consumer.poll(Duration.ofMillis(100));
        }
        consumer.seekToEnd(consumer.assignment());
        for (TopicPartition partition : consumer.assignment()) {
            consumer.position(partition);
        }

        publish.run();

        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        ConsumerRecords<String, String> records = ConsumerRecords.empty();
        while (records.isEmpty() && System.nanoTime() < deadline) {
            records = consumer.poll(Duration.ofMillis(250));
        }
        return records;
    }
}
