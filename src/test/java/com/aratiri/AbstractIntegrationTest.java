package com.aratiri;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

import com.aratiri.infrastructure.persistence.jpa.entity.WebhookEventEntity;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    private static final Object DB_CLEANUP_LOCK = new Object();
    private static final Path TEST_MACAROON = createTestMacaroon();

    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine")
    )
            .withDatabaseName("aratiri_test")
            .withUsername("aratiri_test")
            .withPassword("aratiri_test");

    private static final ConfluentKafkaContainer kafka = new ConfluentKafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.1")
    );

    static {
        kafka.start();
        postgres.start();
    }

    @LocalServerPort
    protected int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ConsumerFactory<String, String> consumerFactory;

    protected WebTestClient webTestClient;

    @BeforeEach
    void cleanDatabase() {
        synchronized (DB_CLEANUP_LOCK) {
            retryOnDeadlock(() -> jdbcTemplate.execute("""
                    TRUNCATE TABLE
                    aratiri.account_entries,
                    aratiri.transaction_events,
                    aratiri.transactions,
                    aratiri.lightning_invoices,
                    aratiri.payment_requests,
                    aratiri.refresh_tokens,
                    aratiri.password_reset_data,
                    aratiri.verification_data,
                    aratiri.accounts,
                    aratiri.users,
                    aratiri.payment_commands,
                    aratiri.webhook_deliveries,
                    aratiri.webhook_events,
                    aratiri.webhook_endpoint_subscriptions,
                    aratiri.webhook_endpoints
                CASCADE
                """));
        }
    }

    /**
     * Async Kafka consumers (settlement, webhook event creation) may still hold row locks from the
     * previous test when the next test truncates; PostgreSQL then deadlocks the truncate against the
     * consumer insert. Retrying the truncate on deadlock keeps tests stable without waiting on
     * consumer queues. A consumer transaction killed by deadlock is retried by Kafka and lands in
     * this test's database, but every assertion filters by unique keys from this test, so stale rows
     * from the previous test are harmless.
     */
    private void retryOnDeadlock(Runnable statement) {
        int attempts = 0;
        while (true) {
            try {
                statement.run();
                return;
            } catch (org.springframework.dao.PessimisticLockingFailureException e) {
                attempts++;
                if (attempts >= 10) {
                    throw e;
                }
                LockSupport.parkNanos(Duration.ofMillis(200).toNanos());
            }
        }
    }

    /**
     * Polls {@code condition} every 100ms until it holds or {@code timeout} elapses, then fails
     * with an assertion error. Replaces {@code Thread.sleep} polling (sonarlint S2925): the poll
     * interval is backed by {@link LockSupport#parkNanos} and the deadline is wall-clock based, so
     * the wait is deterministic regardless of build load.
     */
    protected static void awaitUntil(String description, BooleanSupplier condition, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            LockSupport.parkNanos(Duration.ofMillis(100).toNanos());
        }
        throw new AssertionError("Timed out after " + timeout + " waiting for " + description);
    }

    /**
     * Polls a topic from its beginning with a fresh consumer group and returns every record seen
     * within the window. Callers filter by the unique record key, so records published by earlier
     * tests on the same topic are harmless.
     */
    protected List<ConsumerRecord<String, String>> pollKafkaRecords(String topic, Duration timeout) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                consumerFactory.getConfigurationProperties().get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG));
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-poll-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        List<ConsumerRecord<String, String>> matching = new ArrayList<>();
        try (Consumer<String, String> consumer = new org.apache.kafka.clients.consumer.KafkaConsumer<>(props)) {
            consumer.subscribe(Collections.singletonList(topic));
            long deadline = System.nanoTime() + timeout.toNanos();
            while (System.nanoTime() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(250));
                if (consumer.assignment().isEmpty()) {
                    continue;
                }
                for (ConsumerRecord<String, String> consumerRecord : records.records(topic)) {
                    matching.add(consumerRecord);
                }
                if (records.isEmpty() && !matching.isEmpty()) {
                    break;
                }
            }
        }
        return matching;
    }

    /**
     * Polls a topic from its beginning for the full window (no early break) and returns the first
     * record matching the predicate. Use for records that arrive long after the poll starts (for
     * example dead-lettered records behind retry-topic backoff).
     */
    protected Optional<ConsumerRecord<String, String>> pollKafkaRecordMatching(
            String topic,
            Duration timeout,
            java.util.function.Predicate<ConsumerRecord<String, String>> predicate
    ) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                consumerFactory.getConfigurationProperties().get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG));
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-poll-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        try (Consumer<String, String> consumer = new org.apache.kafka.clients.consumer.KafkaConsumer<>(props)) {
            consumer.subscribe(Collections.singletonList(topic));
            long deadline = System.nanoTime() + timeout.toNanos();
            while (System.nanoTime() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(250));
                if (consumer.assignment().isEmpty()) {
                    continue;
                }
                for (ConsumerRecord<String, String> consumerRecord : records.records(topic)) {
                    if (predicate.test(consumerRecord)) {
                        return Optional.of(consumerRecord);
                    }
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Waits for a {@code webhook_events} row with the given event key to appear. Webhook rows are
     * created asynchronously by {@code WebhookEventConsumer} after the business transaction
     * commits, so assertions must poll rather than read immediately.
     */
    protected WebhookEventEntity awaitWebhookEvent(
            com.aratiri.infrastructure.persistence.jpa.repository.WebhookEventRepository repository,
            String eventKey,
            Duration timeout
    ) {
        awaitUntil("webhook event with key " + eventKey + " to be created",
                () -> repository.findByEventKey(eventKey).isPresent(),
                timeout);
        return repository.findByEventKey(eventKey).orElseThrow();
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("lnd.path.macaroon.admin", TEST_MACAROON::toString);
    }

    protected String baseUrl() {
        return "http://localhost:" + port;
    }

    protected WebTestClient webTestClient() {
        if (this.webTestClient == null) {
            this.webTestClient = WebTestClient.bindToServer()
                    .baseUrl(baseUrl())
                    .responseTimeout(Duration.ofSeconds(30))
                    .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                    .build();
        }
        return this.webTestClient;
    }

    private static Path createTestMacaroon() {
        try {
            Path macaroon = Files.createTempFile("aratiri-test-macaroon-", ".macaroon");
            Files.writeString(macaroon, "00", StandardCharsets.US_ASCII);
            macaroon.toFile().deleteOnExit();
            return macaroon;
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
