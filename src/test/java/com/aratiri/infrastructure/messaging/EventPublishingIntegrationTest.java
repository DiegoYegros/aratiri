package com.aratiri.infrastructure.messaging;

import com.aratiri.AbstractIntegrationTest;
import com.aratiri.accounts.application.port.out.CurrencyConversionPort;
import com.aratiri.accounts.application.port.out.LightningAddressPort;
import com.aratiri.auth.application.dto.RegistrationRequestDTO;
import com.aratiri.auth.application.dto.VerificationRequestDTO;
import com.aratiri.auth.application.port.out.EmailNotificationPort;
import com.aratiri.infrastructure.messaging.listener.LightningListener;
import com.aratiri.infrastructure.messaging.listener.OnChainTransactionListener;
import com.aratiri.infrastructure.persistence.jpa.repository.AccountRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.VerificationDataRepository;
import com.aratiri.payments.application.event.PaymentSentEvent;
import com.aratiri.transactions.application.ExternalDebitCompletionSettlement;
import com.aratiri.transactions.application.TransactionSettlementModule;
import com.aratiri.transactions.application.dto.CreateTransactionRequest;
import com.aratiri.transactions.application.dto.TransactionCurrency;
import com.aratiri.transactions.application.dto.TransactionDTOResponse;
import com.aratiri.transactions.application.dto.TransactionStatus;
import com.aratiri.transactions.application.dto.TransactionType;
import com.aratiri.transactions.application.port.in.TransactionsPort;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * End-to-end coverage for direct Kafka publishing (replacing the outbox): events published by a
 * transactional service surface on the real topic with the original key/payload, and a listener
 * receives an event published outside any transaction.
 */
@Import(EventPublishingIntegrationTest.DirectPublishListener.class)
class EventPublishingIntegrationTest extends AbstractIntegrationTest {

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
    private EventPublisher eventPublisher;

    @Autowired
    private TransactionsPort transactionsPort;

    @Autowired
    private TransactionSettlementModule transactionSettlementModule;

    @Autowired
    private VerificationDataRepository verificationDataRepository;

    @Autowired
    private AccountRepository accountRepository;

    private String userId;

    @BeforeEach
    void setUp() {
        String email = "event-publisher-test@example.com";
        String password = "SecurePass123!";
        String alias = "eventpublisher";
        when(currencyConversionPort.getCurrentBtcPrice()).thenReturn(Map.of("usd", BigDecimal.valueOf(50000)));
        when(lightningAddressPort.generateTaprootAddress()).thenReturn("bc1p_event_publish_test");
        doAnswer(invocation -> null).when(emailNotificationPort).sendVerificationEmail(anyString(), anyString());

        webTestClient().post().uri("/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(registration("Event Publish Test", email, password, alias))
                .exchange()
                .expectStatus().isCreated();
        String verificationCode = verificationDataRepository.findById(email).orElseThrow().getCode();
        webTestClient().post().uri("/v1/auth/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(verification(email, verificationCode))
                .exchange()
                .expectStatus().isOk();

        this.userId = accountRepository.findByAlias(alias).orElseThrow().getUser().getId();
    }

    @Test
    @DisplayName("Transactional service settlement publishes payment.sent after commit with original key and payload")
    void settleExternalDebit_publishesPaymentSentAfterCommit() throws Exception {
        transactionSettlementModule.settleOnChainCredit(new com.aratiri.transactions.application.OnChainCreditSettlement(
                userId, 5_000L, "event-publish-funding-tx", 0L));

        TransactionDTOResponse debit = transactionsPort.createTransaction(CreateTransactionRequest.builder()
                .userId(userId)
                .amountSat(1_500L)
                .currency(TransactionCurrency.BTC)
                .type(TransactionType.LIGHTNING_DEBIT)
                .status(TransactionStatus.PENDING)
                .description("Direct publish payment")
                .referenceId("event-publish-payment-hash")
                .build());

        transactionSettlementModule.settleExternalDebit(
                new ExternalDebitCompletionSettlement(debit.getId(), userId));

        List<ConsumerRecord<String, String>> records = pollKafkaRecords(
                KafkaTopicNames.PAYMENT_SENT, Duration.ofSeconds(15));
        ConsumerRecord<String, String> consumerRecord = records.stream()
                .filter(r -> debit.getId().equals(r.key()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no payment.sent record for transaction " + debit.getId()));

        PaymentSentEvent event = new JsonMapper().readValue(consumerRecord.value(), PaymentSentEvent.class);
        assertEquals(debit.getId(), event.getTransactionId());
        assertEquals(userId, event.getUserId());
        assertEquals(1_500L, event.getAmount());
        assertEquals("event-publish-payment-hash", event.getPaymentHash());
    }

    @Test
    @DisplayName("Direct publish outside a transaction reaches a Kafka listener")
    void directPublish_reachesListener() throws Exception {
        DirectPublishListener.reset();
        String key = "direct-key-" + UUID.randomUUID();
        PaymentSentEvent event = new PaymentSentEvent(userId, "tx-direct", 100L, null, null, "direct");

        eventPublisher.publish(DirectPublishListener.TOPIC, key, event);

        assertTrue(DirectPublishListener.LATCH.await(15, TimeUnit.SECONDS),
                "listener did not receive the directly published event");
        assertEquals(key, DirectPublishListener.receivedKey);
        PaymentSentEvent received = new JsonMapper().readValue(DirectPublishListener.receivedValue, PaymentSentEvent.class);
        assertEquals("tx-direct", received.getTransactionId());
        assertEquals(100L, received.getAmount());
    }

    @Component
    static class DirectPublishListener {

        static final String TOPIC = "test.direct.publish";
        static final CountDownLatch LATCH = new CountDownLatch(1);
        static volatile String receivedKey;
        static volatile String receivedValue;

        @KafkaListener(topics = TOPIC, groupId = "test-direct-publish-group")
        public void listen(String message, @Header(KafkaHeaders.RECEIVED_KEY) String key) {
            receivedKey = key;
            receivedValue = message;
            LATCH.countDown();
        }

        static void reset() {
            receivedKey = null;
            receivedValue = null;
        }
    }

    private static RegistrationRequestDTO registration(String name, String email, String password, String alias) {
        RegistrationRequestDTO request = new RegistrationRequestDTO();
        request.setName(name);
        request.setEmail(email);
        request.setPassword(password);
        request.setAlias(alias);
        return request;
    }

    private static VerificationRequestDTO verification(String email, String code) {
        VerificationRequestDTO request = new VerificationRequestDTO();
        request.setEmail(email);
        request.setCode(code);
        return request;
    }
}
