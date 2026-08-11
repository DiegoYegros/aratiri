package com.aratiri.payments;

import com.aratiri.AbstractIntegrationTest;
import com.aratiri.accounts.application.port.out.CurrencyConversionPort;
import com.aratiri.accounts.application.port.out.LightningAddressPort;
import com.aratiri.auth.application.dto.RegistrationRequestDTO;
import com.aratiri.auth.application.dto.VerificationRequestDTO;
import com.aratiri.auth.application.port.out.EmailNotificationPort;
import com.aratiri.infrastructure.messaging.KafkaTopicNames;
import com.aratiri.infrastructure.messaging.listener.LightningListener;
import com.aratiri.infrastructure.messaging.listener.OnChainTransactionListener;
import com.aratiri.infrastructure.persistence.jpa.entity.TransactionEntity;
import com.aratiri.infrastructure.persistence.jpa.repository.AccountEntryRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.AccountRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.TransactionsRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.VerificationDataRepository;
import com.aratiri.invoices.domain.DecodedLightningInvoice;
import com.aratiri.payments.application.dto.OnChainPaymentDTOs;
import com.aratiri.payments.application.dto.PayInvoiceRequestDTO;
import com.aratiri.payments.application.event.OnChainPaymentInitiatedEvent;
import com.aratiri.payments.application.event.PaymentInitiatedEvent;
import com.aratiri.payments.domain.LightningPayment;
import com.aratiri.payments.domain.LightningPaymentStatus;
import com.aratiri.payments.domain.OnChainFeeEstimate;
import com.aratiri.transactions.application.dto.TransactionStatus;
import com.aratiri.transactions.application.dto.TransactionType;
import com.aratiri.transactions.application.port.in.TransactionsPort;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyInt;

/**
 * End-to-end coverage for the Kafka execution consumers that replaced node_operations: a payment
 * initiated through the API is executed by the consumer, settles the transaction, and redelivered
 * events do not execute twice.
 */
class PaymentExecutionIntegrationTest extends AbstractIntegrationTest {

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
    private com.aratiri.invoices.application.port.out.LightningNodePort invoicesLightningNodePort;

    @MockitoBean
    private com.aratiri.payments.application.port.out.LightningNodePort paymentsLightningNodePort;

    @Autowired
    private VerificationDataRepository verificationDataRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionsRepository transactionsRepository;

    @Autowired
    private com.aratiri.infrastructure.persistence.jpa.repository.WebhookEventRepository webhookEventRepository;

    @Autowired
    private AccountEntryRepository accountEntryRepository;

    @Autowired
    private TransactionsPort transactionsPort;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    private final AtomicInteger sendOnChainCalls = new AtomicInteger();
    private final AtomicInteger executeSendPaymentCalls = new AtomicInteger();
    private final Map<String, String> broadcastLabels = new ConcurrentHashMap<>();

    private String userId;
    private String userToken;

    @DynamicPropertySource
    static void uniqueExecutionConsumerGroups(DynamicPropertyRegistry registry) {
        registry.add("aratiri.kafka.groups.lightning-payment-execution",
                () -> "lightning-payment-execution-test-" + UUID.randomUUID());
        registry.add("aratiri.kafka.groups.onchain-send-execution",
                () -> "onchain-send-execution-test-" + UUID.randomUUID());
    }

    @BeforeEach
    void setUp() {
        sendOnChainCalls.set(0);
        executeSendPaymentCalls.set(0);
        broadcastLabels.clear();

        when(currencyConversionPort.getCurrentBtcPrice()).thenReturn(Map.of("usd", BigDecimal.valueOf(50000)));
        when(lightningAddressPort.generateTaprootAddress()).thenReturn("bc1p_execution_test");
        doAnswer(invocation -> null).when(emailNotificationPort).sendVerificationEmail(anyString(), anyString());
        doAnswer(invocation -> {
            String invoice = invocation.getArgument(0);
            return new DecodedLightningInvoice(
                    paymentHashOf(invoice), 2_000L, "execution test invoice",
                    null, 3600L, "02destination", 144L, "payment-addr",
                    java.time.Instant.parse("2026-01-01T00:00:00Z"), null, List.of()
            );
        }).when(invoicesLightningNodePort).decodePaymentRequest(anyString());
        doAnswer(invocation -> {
            executeSendPaymentCalls.incrementAndGet();
            return Optional.of(new LightningPayment(
                    LightningPaymentStatus.SUCCEEDED, "FAILURE_REASON_NONE", 0, 0));
        }).when(paymentsLightningNodePort).executeLightningPayment(any(), anyInt(), anyInt());
        when(paymentsLightningNodePort.findPayment(anyString())).thenReturn(Optional.empty());
        when(paymentsLightningNodePort.estimateOnChainFee(any())).thenReturn(new OnChainFeeEstimate(200L, 10L));
        when(paymentsLightningNodePort.sendOnChain(any(), anyString())).thenAnswer(invocation -> {
            sendOnChainCalls.incrementAndGet();
            String label = invocation.getArgument(1);
            String txid = "txid-" + sendOnChainCalls.get();
            broadcastLabels.put(label, txid);
            return txid;
        });
        when(paymentsLightningNodePort.findOnChainTxIdByLabel(anyString())).thenAnswer(invocation ->
                Optional.ofNullable(broadcastLabels.get(invocation.getArgument(0))));

        this.userToken = registerUser("execution-test@example.com", "executiontest");
        transactionsPort.createAndSettleTransaction(com.aratiri.transactions.application.dto.CreateTransactionRequest.builder()
                .userId(userId)
                .amountSat(10_000L)
                .currency(com.aratiri.transactions.application.dto.TransactionCurrency.BTC)
                .type(TransactionType.ONCHAIN_CREDIT)
                .description("Seed balance")
                .referenceId("execution-seed")
                .build());
    }

    @Test
    @DisplayName("API lightning payment is executed by the Kafka consumer and settles the transaction")
    void lightningPayment_apiInitiated_executedByConsumerAndSettles() {
        String invoice = "lnbc1" + "a1b2c3d4e5f6";
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("invoice", invoice);

        webTestClient().post().uri("/v1/payments/invoice")
                .header("Authorization", "Bearer " + userToken)
                .header("Idempotency-Key", "execution-lightning-" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isAccepted();

        TransactionEntity settled = awaitTransactionStatus(TransactionType.LIGHTNING_DEBIT);
        assertEquals(TransactionStatus.COMPLETED.name(), settled.getCurrentStatus());
        assertEquals(1, executeSendPaymentCalls.get(), "consumer must execute the send exactly once");

        assertTrue(accountEntryRepository.findAll().stream()
                        .anyMatch(entry -> settled.getId().equals(entry.getTransactionId())),
                "settlement must append a ledger entry");

        List<ConsumerRecord<String, String>> records = pollKafkaRecords(
                KafkaTopicNames.PAYMENT_SENT, Duration.ofSeconds(15));
        assertTrue(records.stream().anyMatch(consumerRecord -> settled.getId().equals(consumerRecord.key())),
                "payment.sent must be published for the settled transaction");
    }

    @Test
    @DisplayName("Redelivered lightning payment is skipped once the transaction is terminal")
    void lightningPayment_redeliveryAfterSettlement_doesNotExecuteAgain() throws Exception {
        String invoice = "lnbc1" + "f6e5d4c3b2a1";
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("invoice", invoice);

        webTestClient().post().uri("/v1/payments/invoice")
                .header("Authorization", "Bearer " + userToken)
                .header("Idempotency-Key", "execution-redelivery-" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isAccepted();

        TransactionEntity settled = awaitTransactionStatus(TransactionType.LIGHTNING_DEBIT);
        assertEquals(TransactionStatus.COMPLETED.name(), settled.getCurrentStatus());
        assertEquals(1, executeSendPaymentCalls.get());

        PaymentInitiatedEvent event = new PaymentInitiatedEvent(
                userId, settled.getId(), paymentRequest(invoice));
        kafkaTemplate.send(KafkaTopicNames.PAYMENT_INITIATED, settled.getId(),
                new JsonMapper().writeValueAsString(event)).get();

        LockSupport.parkNanos(Duration.ofSeconds(2).toNanos());
        assertEquals(1, executeSendPaymentCalls.get(), "redelivery must not execute the send again");
        assertEquals(1, accountEntryRepository.findAll().stream()
                .filter(entry -> settled.getId().equals(entry.getTransactionId()))
                .count(), "redelivery must not duplicate ledger entries");
    }

    @Test
    @DisplayName("API on-chain payment is broadcast by the consumer and settles the transaction")
    void onChainSend_apiInitiated_broadcastByConsumerAndSettles() {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("address", "tb1qdestination");
        body.put("sats_amount", 3_000L);

        webTestClient().post().uri("/v1/payments/onchain")
                .header("Authorization", "Bearer " + userToken)
                .header("Idempotency-Key", "execution-onchain-" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isAccepted();

        TransactionEntity settled = awaitTransactionStatus(TransactionType.ONCHAIN_DEBIT);
        assertEquals(TransactionStatus.COMPLETED.name(), settled.getCurrentStatus());
        assertEquals(1, sendOnChainCalls.get(), "consumer must broadcast exactly once");
        assertTrue(broadcastLabels.containsKey("aratiri-onchain-" + settled.getId()),
                "broadcast must carry the transaction label for redelivery recovery");
    }

    @Test
    @DisplayName("Poison payment.initiated message dead-letters with delivery-attempt and exception headers")
    void poisonMessage_deadLettersWithRetryMetadataHeaders() throws Exception {
        String poison = "{poison-" + UUID.randomUUID();
        String dltTopic = KafkaTopicNames.PAYMENT_INITIATED + "-dlt";
        kafkaTemplate.send(KafkaTopicNames.PAYMENT_INITIATED, "poison-" + UUID.randomUUID(), poison).get();

        ConsumerRecord<String, String> deadLetterRecord = pollKafkaRecordMatching(
                dltTopic, Duration.ofSeconds(90), r -> poison.equals(r.value()))
                .orElseThrow(() -> new AssertionError("poison record not found on " + dltTopic));

        assertNotNull(deadLetterRecord.headers().lastHeader("retry_topic-attempts"),
                "DLT record must carry the retry attempt count header feeding the unknown-outcome webhook facts");
        assertNotNull(deadLetterRecord.headers().lastHeader("kafka_exception-message"),
                "DLT record must carry the exception-message header feeding the unknown-outcome webhook facts");
    }

    @Test
    @DisplayName("Exhausted payment execution dead-letters and emits the unknown-outcome webhook with retry metadata")
    void exhaustedExecution_emitsUnknownOutcomeWebhookWithAttemptCount() throws Exception {
        String transactionId = UUID.randomUUID().toString();
        PayInvoiceRequestDTO request = new PayInvoiceRequestDTO();
        request.setInvoice("lnbc1" + "deadbeefdeadbeef");
        PaymentInitiatedEvent event = new PaymentInitiatedEvent(userId, transactionId, request);
        kafkaTemplate.send(KafkaTopicNames.PAYMENT_INITIATED, transactionId,
                new JsonMapper().writeValueAsString(event)).get();

        long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        while (System.nanoTime() < deadline) {
            Optional<com.aratiri.infrastructure.persistence.jpa.entity.WebhookEventEntity> webhook =
                    webhookEventRepository.findByEventKey("node_operation.unknown_outcome:" + transactionId);
            if (webhook.isPresent()) {
                assertTrue(webhook.get().getPayload().contains("Transaction not found for execution: " + transactionId),
                        "webhook must carry the DLT exception message");
                assertTrue(webhook.get().getPayload().contains("\"attempt_count\":5"),
                        "webhook must carry the final retry attempt count");
                assertTrue(webhook.get().getPayload().contains("\"operation_id\":\"" + transactionId + "\""),
                        "webhook operationId must be the transactionId");
                return;
            }
            sleep(500);
        }
        throw new AssertionError("unknown-outcome webhook was not emitted for transaction " + transactionId);
    }

    @Test
    @DisplayName("On-chain redelivery with a prior broadcast recovers via the label without re-sending")
    void onChainSend_redeliveryWithExistingBroadcast_recoversByLabel() throws Exception {
        com.aratiri.transactions.application.dto.TransactionDTOResponse created = transactionsPort.createTransaction(
                com.aratiri.transactions.application.dto.CreateTransactionRequest.builder()
                        .userId(userId)
                        .amountSat(2_500L)
                        .currency(com.aratiri.transactions.application.dto.TransactionCurrency.BTC)
                        .type(TransactionType.ONCHAIN_DEBIT)
                        .status(TransactionStatus.PENDING)
                        .description("Recovery test")
                        .referenceId("recovery-ref")
                        .build());
        String txId = created.getId();
        broadcastLabels.put("aratiri-onchain-" + txId, "crash-before-confirm-txid");

        OnChainPaymentDTOs.SendOnChainRequestDTO request = new OnChainPaymentDTOs.SendOnChainRequestDTO();
        request.setAddress("tb1qdestination");
        request.setSatsAmount(2_500L);
        OnChainPaymentInitiatedEvent event = new OnChainPaymentInitiatedEvent(userId, txId, request);
        kafkaTemplate.send(KafkaTopicNames.ONCHAIN_PAYMENT_INITIATED, txId,
                new JsonMapper().writeValueAsString(event)).get();

        TransactionEntity settled = awaitTransactionStatusById(txId);
        assertEquals(TransactionStatus.COMPLETED.name(), settled.getCurrentStatus());
        assertEquals(0, sendOnChainCalls.get(), "label recovery must not re-broadcast");
    }

    private TransactionEntity awaitTransactionStatus(TransactionType type) {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadline) {
            Optional<TransactionEntity> tx = transactionsRepository.findAll().stream()
                    .filter(t -> t.getType() == type)
                    .filter(t -> !"execution-seed".equals(t.getReferenceId()))
                    .findFirst();
            if (tx.isPresent() && isTerminal(tx.get())) {
                return tx.get();
            }
            sleep(200);
        }
        throw new AssertionError("transaction did not reach a terminal state for " + type);
    }

    private TransactionEntity awaitTransactionStatusById(String transactionId) {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadline) {
            Optional<TransactionEntity> tx = transactionsRepository.findById(transactionId);
            if (tx.isPresent() && isTerminal(tx.get())) {
                return tx.get();
            }
            sleep(200);
        }
        throw new AssertionError("transaction " + transactionId + " did not reach a terminal state");
    }

    private static boolean isTerminal(TransactionEntity transaction) {
        return TransactionStatus.COMPLETED.name().equals(transaction.getCurrentStatus())
                || TransactionStatus.FAILED.name().equals(transaction.getCurrentStatus());
    }

    private static void sleep(long millis) {
        LockSupport.parkNanos(Duration.ofMillis(millis).toNanos());
    }

    private static String paymentHashOf(String invoice) {
        String clean = invoice != null && invoice.startsWith("lnbc1") ? invoice.substring("lnbc1".length()) : invoice;
        return clean == null ? "" : clean;
    }

    private static PayInvoiceRequestDTO paymentRequest(String invoice) {
        PayInvoiceRequestDTO request = new PayInvoiceRequestDTO();
        request.setInvoice(invoice);
        return request;
    }

    private String registerUser(String email, String alias) {
        String password = "SecurePass123!";
        webTestClient().post().uri("/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(registration("Execution Test", email, password, alias))
                .exchange()
                .expectStatus().isCreated();
        String code = verificationDataRepository.findById(email).orElseThrow().getCode();
        com.aratiri.auth.application.dto.AuthResponseDTO tokens = webTestClient().post().uri("/v1/auth/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(verification(email, code))
                .exchange()
                .expectStatus().isOk()
                .expectBody(com.aratiri.auth.application.dto.AuthResponseDTO.class)
                .returnResult().getResponseBody();
        this.userId = accountRepository.findByAlias(alias).orElseThrow().getUser().getId();
        return tokens.getAccessToken();
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
