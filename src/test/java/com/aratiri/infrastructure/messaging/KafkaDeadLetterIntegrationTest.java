package com.aratiri.infrastructure.messaging;

import com.aratiri.AbstractIntegrationTest;
import com.aratiri.accounts.application.port.out.CurrencyConversionPort;
import com.aratiri.accounts.application.port.out.LightningAddressPort;
import com.aratiri.auth.application.port.out.EmailNotificationPort;
import com.aratiri.infrastructure.messaging.listener.LightningListener;
import com.aratiri.infrastructure.messaging.listener.OnChainTransactionListener;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class KafkaDeadLetterIntegrationTest extends AbstractIntegrationTest {

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

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ConsumerFactory<String, String> consumerFactory;

    @Test
    @DisplayName("Poison message on payment.initiated is dead-lettered instead of wedging the partition")
    void poisonMessageLandsInDeadLetterTopic() throws Exception {
        String poisonPayload = "{poison-" + UUID.randomUUID();
        String dltTopic = KafkaTopicNames.PAYMENT_INITIATED + ".DLT";

        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                consumerFactory.getConfigurationProperties().get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG));
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "dlt-test-consumer-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        try (Consumer<String, String> dltConsumer = new org.apache.kafka.clients.consumer.KafkaConsumer<>(props)) {
            dltConsumer.subscribe(Collections.singletonList(dltTopic));

            kafkaTemplate.send(KafkaTopicNames.PAYMENT_INITIATED, "poison-" + UUID.randomUUID(), poisonPayload).get();

            // The shared DefaultErrorHandler retries twice (1s apart) before publishing to the DLT.
            long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
            boolean poisonDeadLettered = false;
            while (!poisonDeadLettered && System.nanoTime() < deadline) {
                ConsumerRecords<String, String> records = dltConsumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> consumerRecord : records.records(dltTopic)) {
                    if (poisonPayload.equals(consumerRecord.value())) {
                        poisonDeadLettered = true;
                        break;
                    }
                }
            }
            assertTrue(poisonDeadLettered, "poison record should be published to " + dltTopic);
        }
    }
}
