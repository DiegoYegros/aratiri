package com.aratiri;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
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

    protected WebTestClient webTestClient;

    @BeforeEach
    void cleanDatabase() {
        synchronized (DB_CLEANUP_LOCK) {
            jdbcTemplate.execute("""
                    TRUNCATE TABLE
                    aratiri.account_entries,
                    aratiri.transaction_events,
                    aratiri.transactions,
                    aratiri.lightning_invoices,
                    aratiri.payment_requests,
                    aratiri.spark_wallets,
                    aratiri.outbox_events,
                    aratiri.refresh_tokens,
                    aratiri.password_reset_data,
                    aratiri.verification_data,
                    aratiri.accounts,
                    aratiri.users,
                    aratiri.payment_commands,
                    aratiri.node_operations,
                    aratiri.webhook_deliveries,
                    aratiri.webhook_events,
                    aratiri.webhook_endpoint_subscriptions,
                    aratiri.webhook_endpoints
                CASCADE
                """);
        }
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
