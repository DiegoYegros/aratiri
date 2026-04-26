package com.aratiri.payments;

import com.aratiri.AbstractIntegrationTest;
import com.aratiri.auth.application.dto.AuthResponseDTO;
import com.aratiri.auth.application.dto.RegistrationRequestDTO;
import com.aratiri.auth.application.dto.VerificationRequestDTO;
import com.aratiri.auth.application.port.out.EmailNotificationPort;
import com.aratiri.accounts.application.port.out.CurrencyConversionPort;
import com.aratiri.accounts.application.port.out.LightningAddressPort;
import com.aratiri.infrastructure.persistence.jpa.repository.VerificationDataRepository;
import com.aratiri.payments.application.PaymentCommandService;
import com.aratiri.payments.application.command.PaymentCommandFailurePayload;
import com.aratiri.payments.infrastructure.json.JsonUtils;
import com.aratiri.shared.exception.AratiriException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

class PaymentIdempotencyIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean
    private EmailNotificationPort emailNotificationPort;

    @MockitoBean
    private CurrencyConversionPort currencyConversionPort;

    @MockitoBean
    private LightningAddressPort lightningAddressPort;

    @Autowired
    private VerificationDataRepository verificationDataRepository;

    @Autowired
    private PaymentCommandService paymentCommandService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String userId;

    @BeforeEach
    void setupUserAndAccount() {
        String email = "idempotency-test@example.com";
        String password = "SecurePass123!";
        String name = "Idempotency Test";
        String alias = "idempotencytest";

        when(currencyConversionPort.getCurrentBtcPrice()).thenReturn(Map.of("usd", BigDecimal.valueOf(50000)));
        when(lightningAddressPort.generateTaprootAddress()).thenReturn("bc1p_idempotency_test");
        doAnswer(invocation -> null).when(emailNotificationPort).sendVerificationEmail(anyString(), anyString());

        webTestClient().post().uri("/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createRegistrationRequest(name, email, password, alias))
                .exchange()
                .expectStatus().isCreated();

        String verificationCode = verificationDataRepository.findById(email)
                .orElseThrow()
                .getCode();

        webTestClient().post().uri("/v1/auth/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createVerificationRequest(email, verificationCode))
                .exchange()
                .expectStatus().isOk()
                .expectBody(AuthResponseDTO.class)
                .returnResult().getResponseBody();

        this.userId = jdbcTemplate.queryForObject(
                "SELECT id FROM aratiri.users WHERE email = ?", String.class, email
        );
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Same key and same payload returns same command id")
    void sameKeySamePayload_returnsSameCommandId() {
        String idempotencyKey = UUID.randomUUID().toString();
        String payload = "{\"invoice\":\"lnbc1test123\"}";

        PaymentCommandService.PaymentCommandResult first = paymentCommandService.resolveIdempotency(
                userId, idempotencyKey, "LIGHTNING_INVOICE_PAY", payload
        );
        assertEquals(PaymentCommandService.PaymentCommandResult.ResultType.NEW_COMMAND, first.type());
        assertNotNull(first.commandId());

        PaymentCommandService.PaymentCommandResult second = paymentCommandService.resolveIdempotency(
                userId, idempotencyKey, "LIGHTNING_INVOICE_PAY", payload
        );
        assertEquals(PaymentCommandService.PaymentCommandResult.ResultType.IN_PROGRESS, second.type());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Same key and different payload returns 409")
    void sameKeyDifferentPayload_returns409() {
        String idempotencyKey = UUID.randomUUID().toString();
        String firstPayload = "{\"invoice\":\"lnbc1test123\"}";

        paymentCommandService.resolveIdempotency(userId, idempotencyKey, "LIGHTNING_INVOICE_PAY", firstPayload);

        String secondPayload = "{\"invoice\":\"lnbc1test456\"}";

        AratiriException exception = assertThrows(AratiriException.class, () ->
                paymentCommandService.resolveIdempotency(userId, idempotencyKey, "LIGHTNING_INVOICE_PAY", secondPayload)
        );
        assertEquals(HttpStatus.CONFLICT.value(), exception.getStatus());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Concurrent same-key requests resolve without error")
    void concurrentSameKeyRequests_resolvesWithoutError() throws InterruptedException {
        String idempotencyKey = UUID.randomUUID().toString();
        String payload = "{\"invoice\":\"lnbc1concurrent\"}";

        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicReference<UUID> firstCommandId = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    PaymentCommandService.PaymentCommandResult result = paymentCommandService.resolveIdempotency(
                            userId, idempotencyKey, "LIGHTNING_INVOICE_PAY", payload
                    );
                    if (result.type() == PaymentCommandService.PaymentCommandResult.ResultType.NEW_COMMAND
                            && firstCommandId.compareAndSet(null, result.commandId())) {
                        // First thread to set
                    }
                } catch (Throwable t) {
                    error.compareAndSet(null, t);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertNull(error.get(), "Concurrent requests should not throw: " + (error.get() != null ? error.get().getMessage() : ""));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Payment command creates one row per idempotency key")
    void paymentCommand_createsOneRowPerKey() {
        String idempotencyKey = UUID.randomUUID().toString();
        String payload = "{\"invoice\":\"lnbc1testcount\"}";

        paymentCommandService.resolveIdempotency(userId, idempotencyKey, "LIGHTNING_INVOICE_PAY", payload);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM aratiri.payment_commands WHERE user_id = ? AND idempotency_key = ?",
                Integer.class, userId, idempotencyKey
        );
        assertEquals(1, count);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Completed command returns replay on subsequent call")
    void completedCommand_returnsReplay() {
        String idempotencyKey = UUID.randomUUID().toString();
        String payload = "{\"invoice\":\"lnbc1testreplay\"}";

        PaymentCommandService.PaymentCommandResult first = paymentCommandService.resolveIdempotency(
                userId, idempotencyKey, "LIGHTNING_INVOICE_PAY", payload
        );
        paymentCommandService.completeCommand(first.commandId(), "tx-123", "{\"transactionId\":\"tx-123\"}");

        PaymentCommandService.PaymentCommandResult second = paymentCommandService.resolveIdempotency(
                userId, idempotencyKey, "LIGHTNING_INVOICE_PAY", payload
        );
        assertEquals(PaymentCommandService.PaymentCommandResult.ResultType.REPLAY, second.type());
        assertEquals("tx-123", second.transactionId());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Failed command returns failed replay on subsequent call")
    void failedCommand_returnsFailedReplay() {
        String idempotencyKey = UUID.randomUUID().toString();
        String payload = "{\"invoice\":\"lnbc1testfailed\"}";
        PaymentCommandFailurePayload failure = new PaymentCommandFailurePayload(
                "Invoice has already been paid",
                HttpStatus.BAD_REQUEST.value()
        );

        PaymentCommandService.PaymentCommandResult first = paymentCommandService.resolveIdempotency(
                userId, idempotencyKey, "LIGHTNING_INVOICE_PAY", payload
        );
        paymentCommandService.failCommand(first.commandId(), JsonUtils.toJson(failure));

        PaymentCommandService.PaymentCommandResult second = paymentCommandService.resolveIdempotency(
                userId, idempotencyKey, "LIGHTNING_INVOICE_PAY", payload
        );

        assertEquals(PaymentCommandService.PaymentCommandResult.ResultType.FAILED_REPLAY, second.type());
        PaymentCommandFailurePayload replayedFailure = JsonUtils.fromJson(
                second.responsePayload(), PaymentCommandFailurePayload.class
        );
        assertEquals("Invoice has already been paid", replayedFailure.message());
        assertEquals(HttpStatus.BAD_REQUEST.value(), replayedFailure.status());
    }

    private RegistrationRequestDTO createRegistrationRequest(String name, String email, String password, String alias) {
        RegistrationRequestDTO request = new RegistrationRequestDTO();
        request.setName(name);
        request.setEmail(email);
        request.setPassword(password);
        request.setAlias(alias);
        return request;
    }

    private VerificationRequestDTO createVerificationRequest(String email, String code) {
        VerificationRequestDTO request = new VerificationRequestDTO();
        request.setEmail(email);
        request.setCode(code);
        return request;
    }
}
