package com.aratiri;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    private static final Object DB_CLEANUP_LOCK = new Object();

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
}
