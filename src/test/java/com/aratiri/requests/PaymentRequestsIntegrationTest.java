package com.aratiri.requests;

import com.aratiri.AbstractIntegrationTest;
import com.aratiri.accounts.application.port.out.CurrencyConversionPort;
import com.aratiri.accounts.application.port.out.LightningAddressPort;
import com.aratiri.auth.application.dto.AuthResponseDTO;
import com.aratiri.auth.application.dto.RegistrationRequestDTO;
import com.aratiri.auth.application.dto.VerificationRequestDTO;
import com.aratiri.auth.application.port.out.EmailNotificationPort;
import com.aratiri.infrastructure.persistence.jpa.entity.PaymentRequestEntity;
import com.aratiri.infrastructure.persistence.jpa.repository.PaymentRequestRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.VerificationDataRepository;
import com.aratiri.invoices.application.InvoiceStateUpdate;
import com.aratiri.invoices.application.port.in.InvoiceSettlementPort;
import com.aratiri.invoices.application.port.out.LightningNodePort;
import com.aratiri.invoices.domain.InvoiceCancelOutcome;
import com.aratiri.invoices.domain.LightningInvoiceCreation;
import com.aratiri.requests.application.dto.CreatePaymentRequestDTO;
import com.aratiri.requests.application.dto.OwnerPaymentRequestDTO;
import com.aratiri.requests.application.port.in.PaymentRequestsPort;
import com.aratiri.requests.domain.exception.PaymentRequestConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.zaxxer.hikari.HikariDataSource;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

class PaymentRequestsIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean
    private EmailNotificationPort emailNotificationPort;

    @MockitoBean
    private CurrencyConversionPort currencyConversionPort;

    @MockitoBean
    private LightningAddressPort lightningAddressPort;

    @MockitoBean
    private LightningNodePort lightningNodePort;

    @Autowired
    private VerificationDataRepository verificationDataRepository;

    @Autowired
    private PaymentRequestRepository paymentRequestRepository;

    @Autowired
    private InvoiceSettlementPort invoiceSettlementPort;

    @Autowired
    private PaymentRequestsPort paymentRequestsPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String ownerToken;
    private String otherToken;

    @BeforeEach
    void setUpUsersAndMocks() {
        when(currencyConversionPort.getCurrentBtcPrice()).thenReturn(Map.of("usd", BigDecimal.valueOf(50000)));
        when(lightningAddressPort.generateTaprootAddress()).thenAnswer(invocation ->
                "bc1p_payment_request_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        doAnswer(invocation -> null).when(emailNotificationPort).sendVerificationEmail(anyString(), anyString());

        when(lightningNodePort.createInvoice(anyLong(), anyString(), any(byte[].class), any(byte[].class), anyLong()))
                .thenAnswer(invocation -> {
                    long amount = invocation.getArgument(0);
                    long expiry = invocation.getArgument(4);
                    byte[] hash = invocation.getArgument(3);
                    String paymentHash = bytesToHex(hash);
                    return new LightningInvoiceCreation("lnbc" + amount + "req" + paymentHash, paymentHash, expiry);
                });
        when(lightningNodePort.createInvoice(anyLong(), anyString(), any(byte[].class), any(byte[].class)))
                .thenAnswer(invocation -> {
                    long amount = invocation.getArgument(0);
                    byte[] hash = invocation.getArgument(3);
                    String paymentHash = bytesToHex(hash);
                    return new LightningInvoiceCreation("lnbc" + amount + "req" + paymentHash, paymentHash, 3600L);
                });
        when(lightningNodePort.cancelInvoice(anyString())).thenReturn(InvoiceCancelOutcome.CANCELLED);

        ownerToken = registerAndVerify("owner-pr@example.com", "Owner PR", "ownerpr");
        otherToken = registerAndVerify("other-pr@example.com", "Other PR", "otherpr");
    }

    @Test
    @DisplayName("Create validates amount/expiry and requires Idempotency-Key")
    void create_validation_and_idempotency_header() {
        CreatePaymentRequestDTO body = createBody(1000, "Lunch", 600);

        webTestClient().post().uri("/v1/payment-requests")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isBadRequest();

        CreatePaymentRequestDTO invalidAmount = createBody(0, "Lunch", 600);
        webTestClient().post().uri("/v1/payment-requests")
                .header("Authorization", "Bearer " + ownerToken)
                .header("Idempotency-Key", "val-1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalidAmount)
                .exchange()
                .expectStatus().isBadRequest();

        webTestClient().post().uri("/v1/payment-requests")
                .header("Authorization", "Bearer " + ownerToken)
                .header("Idempotency-Key", "")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isBadRequest();

        webTestClient().post().uri("/v1/payment-requests")
                .header("Authorization", "Bearer " + ownerToken)
                .header("Idempotency-Key", "a".repeat(256))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isBadRequest();

        // Non-ASCII is outside the accepted printable-ASCII shape; Netty rejects header spaces.
        webTestClient().post().uri("/v1/payment-requests")
                .header("Authorization", "Bearer " + ownerToken)
                .header("Idempotency-Key", "bad-key-\u00E9")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isBadRequest();

        webTestClient().post().uri("/v1/payment-requests")
                .header("Authorization", "Bearer " + ownerToken)
                .header("Idempotency-Key", "a".repeat(255))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isCreated();
    }

    @Test
    @DisplayName("Create is idempotent per owner key and conflicts on payload mismatch")
    void create_idempotency_replay_and_conflict() {
        CreatePaymentRequestDTO body = createBody(1500, "Coffee", 900);

        Map<?, ?> first = webTestClient().post().uri("/v1/payment-requests")
                .header("Authorization", "Bearer " + ownerToken)
                .header("Idempotency-Key", "idem-create-1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult().getResponseBody();

        assertNotNull(first);
        String publicId = (String) first.get("public_id");
        assertNotNull(publicId);
        assertFalse(publicId.matches("\\d+"));
        assertEquals("http://localhost:3000/pay/" + publicId, first.get("share_url"));
        assertEquals(1, paymentRequestRepository.count());

        Map<?, ?> replay = webTestClient().post().uri("/v1/payment-requests")
                .header("Authorization", "Bearer " + ownerToken)
                .header("Idempotency-Key", "idem-create-1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult().getResponseBody();

        assertNotNull(replay);
        assertEquals(publicId, replay.get("public_id"));
        assertEquals(1, paymentRequestRepository.count());

        CreatePaymentRequestDTO conflictBody = createBody(2000, "Coffee", 900);
        webTestClient().post().uri("/v1/payment-requests")
                .header("Authorization", "Bearer " + ownerToken)
                .header("Idempotency-Key", "idem-create-1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(conflictBody)
                .exchange()
                .expectStatus().isEqualTo(409);

        assertEquals(1, paymentRequestRepository.count());
    }

    @Test
    @DisplayName("Create idempotency treats trimmed/blank memo variants as the same payload")
    void create_idempotency_normalizes_memo() {
        Map<?, ?> first = webTestClient().post().uri("/v1/payment-requests")
                .header("Authorization", "Bearer " + ownerToken)
                .header("Idempotency-Key", "idem-memo-1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createBody(1600, "  Coffee  ", 900))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult().getResponseBody();
        assertNotNull(first);
        String publicId = (String) first.get("public_id");
        assertEquals("Coffee", first.get("memo"));

        Map<?, ?> trimmedReplay = webTestClient().post().uri("/v1/payment-requests")
                .header("Authorization", "Bearer " + ownerToken)
                .header("Idempotency-Key", "idem-memo-1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createBody(1600, "Coffee", 900))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult().getResponseBody();
        assertNotNull(trimmedReplay);
        assertEquals(publicId, trimmedReplay.get("public_id"));

        webTestClient().post().uri("/v1/payment-requests")
                .header("Authorization", "Bearer " + ownerToken)
                .header("Idempotency-Key", "idem-memo-1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createBody(1600, "Tea", 900))
                .exchange()
                .expectStatus().isEqualTo(409);

        Map<?, ?> blankFirst = webTestClient().post().uri("/v1/payment-requests")
                .header("Authorization", "Bearer " + ownerToken)
                .header("Idempotency-Key", "idem-memo-blank")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createBody(1700, null, 900))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult().getResponseBody();
        assertNotNull(blankFirst);
        String blankPublicId = (String) blankFirst.get("public_id");

        Map<?, ?> whitespaceReplay = webTestClient().post().uri("/v1/payment-requests")
                .header("Authorization", "Bearer " + ownerToken)
                .header("Idempotency-Key", "idem-memo-blank")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createBody(1700, "   ", 900))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult().getResponseBody();
        assertNotNull(whitespaceReplay);
        assertEquals(blankPublicId, whitespaceReplay.get("public_id"));

        Map<?, ?> emptyReplay = webTestClient().post().uri("/v1/payment-requests")
                .header("Authorization", "Bearer " + ownerToken)
                .header("Idempotency-Key", "idem-memo-blank")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createBody(1700, "", 900))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult().getResponseBody();
        assertNotNull(emptyReplay);
        assertEquals(blankPublicId, emptyReplay.get("public_id"));
    }

    @Test
    @DisplayName("Owner isolation and cursor pagination")
    void owner_isolation_and_cursor_pagination() {
        for (int i = 0; i < 3; i++) {
            webTestClient().post().uri("/v1/payment-requests")
                    .header("Authorization", "Bearer " + ownerToken)
                    .header("Idempotency-Key", "owner-list-" + i)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(createBody(1000 + i, "m" + i, 600))
                    .exchange()
                    .expectStatus().isCreated();
        }

        Map<?, ?> otherCreate = webTestClient().post().uri("/v1/payment-requests")
                .header("Authorization", "Bearer " + otherToken)
                .header("Idempotency-Key", "other-list-1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createBody(5000, "other", 600))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult().getResponseBody();
        assertNotNull(otherCreate);
        String otherPublicId = (String) otherCreate.get("public_id");

        Map<?, ?> page1 = webTestClient().get().uri("/v1/payment-requests?limit=2")
                .header("Authorization", "Bearer " + ownerToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult().getResponseBody();
        assertNotNull(page1);
        assertEquals(true, page1.get("has_more"));
        assertNotNull(page1.get("next_cursor"));
        assertEquals(2, ((java.util.List<?>) page1.get("payment_requests")).size());

        Map<?, ?> page2 = webTestClient().get()
                .uri(uriBuilder -> uriBuilder.path("/v1/payment-requests")
                        .queryParam("limit", 2)
                        .queryParam("cursor", page1.get("next_cursor"))
                        .build())
                .header("Authorization", "Bearer " + ownerToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult().getResponseBody();
        assertNotNull(page2);
        assertEquals(false, page2.get("has_more"));
        assertEquals(1, ((java.util.List<?>) page2.get("payment_requests")).size());

        webTestClient().get().uri("/v1/payment-requests/" + otherPublicId)
                .header("Authorization", "Bearer " + ownerToken)
                .exchange()
                .expectStatus().isNotFound();

        webTestClient().post().uri("/v1/payment-requests/" + otherPublicId + "/cancel")
                .header("Authorization", "Bearer " + ownerToken)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    @DisplayName("Public GET exposes safe fields with no-store and hides unknown IDs")
    void public_get_safe_fields_and_no_store() {
        Map<?, ?> created = webTestClient().post().uri("/v1/payment-requests")
                .header("Authorization", "Bearer " + ownerToken)
                .header("Idempotency-Key", "public-safe-1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createBody(777, "Public memo", 1200))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult().getResponseBody();
        assertNotNull(created);
        String publicId = (String) created.get("public_id");

        webTestClient().get().uri("/r/" + publicId)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
                .expectBody()
                .jsonPath("$.public_id").isEqualTo(publicId)
                .jsonPath("$.amount_sats").isEqualTo(777)
                .jsonPath("$.memo").isEqualTo("Public memo")
                .jsonPath("$.status").isEqualTo("OPEN")
                .jsonPath("$.payment_request").exists()
                .jsonPath("$.user_id").doesNotExist()
                .jsonPath("$.invoice_id").doesNotExist()
                .jsonPath("$.email").doesNotExist();

        webTestClient().get().uri("/r/doesnotexist000000000000000000")
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
                .expectBody()
                .jsonPath("$.message").isEqualTo("Payment request not found");
    }

    @Test
    @DisplayName("Cancel cancels LND invoice, is idempotent, and conflicts once PAID")
    void cancel_idempotency_and_paid_conflict() {
        Map<?, ?> created = webTestClient().post().uri("/v1/payment-requests")
                .header("Authorization", "Bearer " + ownerToken)
                .header("Idempotency-Key", "cancel-1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createBody(1000, "Cancel me", 600))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult().getResponseBody();
        assertNotNull(created);
        String publicId = (String) created.get("public_id");
        PaymentRequestEntity openEntity = paymentRequestRepository.findByPublicId(publicId).orElseThrow();

        webTestClient().post().uri("/v1/payment-requests/" + publicId + "/cancel")
                .header("Authorization", "Bearer " + ownerToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("CANCELLED")
                .jsonPath("$.payment_request").doesNotExist();

        org.mockito.Mockito.verify(lightningNodePort).cancelInvoice(openEntity.getPaymentHash());

        webTestClient().post().uri("/v1/payment-requests/" + publicId + "/cancel")
                .header("Authorization", "Bearer " + ownerToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("CANCELLED");

        // Idempotent replay must not call LND again.
        org.mockito.Mockito.verify(lightningNodePort, org.mockito.Mockito.times(1))
                .cancelInvoice(openEntity.getPaymentHash());

        Map<?, ?> paidCreate = webTestClient().post().uri("/v1/payment-requests")
                .header("Authorization", "Bearer " + ownerToken)
                .header("Idempotency-Key", "cancel-paid-1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createBody(1100, "Will pay", 600))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult().getResponseBody();
        assertNotNull(paidCreate);
        String paidPublicId = (String) paidCreate.get("public_id");
        PaymentRequestEntity entity = paymentRequestRepository.findByPublicId(paidPublicId).orElseThrow();
        settleLinkedInvoice(entity);

        webTestClient().post().uri("/v1/payment-requests/" + paidPublicId + "/cancel")
                .header("Authorization", "Bearer " + ownerToken)
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    @Test
    @DisplayName("Cancel fails closed when LND cancel RPC fails")
    void cancel_rpcFailureLeavesRequestOpen() {
        Map<?, ?> created = webTestClient().post().uri("/v1/payment-requests")
                .header("Authorization", "Bearer " + ownerToken)
                .header("Idempotency-Key", "cancel-rpc-fail-1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createBody(1300, "Stay open", 600))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult().getResponseBody();
        assertNotNull(created);
        String publicId = (String) created.get("public_id");
        PaymentRequestEntity entity = paymentRequestRepository.findByPublicId(publicId).orElseThrow();

        when(lightningNodePort.cancelInvoice(entity.getPaymentHash()))
                .thenThrow(new com.aratiri.errors.ApplicationException("Error cancelling invoice on LND node", 502));

        webTestClient().post().uri("/v1/payment-requests/" + publicId + "/cancel")
                .header("Authorization", "Bearer " + ownerToken)
                .exchange()
                .expectStatus().isEqualTo(502);

        PaymentRequestEntity stillOpen = paymentRequestRepository.findByPublicId(publicId).orElseThrow();
        assertEquals("OPEN", stillOpen.getStatus());
        assertNull(stillOpen.getCancelledAt());
    }

    @Test
    @DisplayName("Cancel heals to PAID when LND reports already settled; later settlement is idempotent")
    void cancel_alreadySettledOnLndHealsPaidAndSettlementRemainsIdempotent() {
        Map<?, ?> created = webTestClient().post().uri("/v1/payment-requests")
                .header("Authorization", "Bearer " + ownerToken)
                .header("Idempotency-Key", "cancel-settled-1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createBody(1400, "Settled race", 600))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult().getResponseBody();
        assertNotNull(created);
        String publicId = (String) created.get("public_id");
        PaymentRequestEntity entity = paymentRequestRepository.findByPublicId(publicId).orElseThrow();

        when(lightningNodePort.cancelInvoice(entity.getPaymentHash()))
                .thenReturn(InvoiceCancelOutcome.ALREADY_SETTLED);

        webTestClient().post().uri("/v1/payment-requests/" + publicId + "/cancel")
                .header("Authorization", "Bearer " + ownerToken)
                .exchange()
                .expectStatus().isEqualTo(409);

        PaymentRequestEntity healed = paymentRequestRepository.findByPublicId(publicId).orElseThrow();
        assertEquals("PAID", healed.getStatus());
        assertNotNull(healed.getPaidAt());
        assertNull(healed.getCancelledAt());

        webTestClient().get().uri("/r/" + publicId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("PAID")
                .jsonPath("$.payment_request").doesNotExist();

        webTestClient().get().uri("/v1/payment-requests/" + publicId)
                .header("Authorization", "Bearer " + ownerToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("PAID")
                .jsonPath("$.payment_request").doesNotExist();

        Instant paidAt = healed.getPaidAt();
        settleLinkedInvoice(healed);
        PaymentRequestEntity stillPaid = paymentRequestRepository.findByPublicId(publicId).orElseThrow();
        assertEquals("PAID", stillPaid.getStatus());
        assertEquals(paidAt, stillPaid.getPaidAt());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Cancel does not hold a DB transaction across the LND cancel RPC")
    void cancel_doesNotHoldTransactionAcrossLndRpc() {
        Map<?, ?> created = webTestClient().post().uri("/v1/payment-requests")
                .header("Authorization", "Bearer " + ownerToken)
                .header("Idempotency-Key", "cancel-tx-bound-1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createBody(1450, "No tx across rpc", 600))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult().getResponseBody();
        assertNotNull(created);
        String publicId = (String) created.get("public_id");
        PaymentRequestEntity entity = paymentRequestRepository.findByPublicId(publicId).orElseThrow();

        AtomicBoolean transactionActiveDuringRpc = new AtomicBoolean(true);
        when(lightningNodePort.cancelInvoice(entity.getPaymentHash())).thenAnswer(invocation -> {
            transactionActiveDuringRpc.set(
                    org.springframework.transaction.support.TransactionSynchronizationManager
                            .isActualTransactionActive()
            );
            return InvoiceCancelOutcome.CANCELLED;
        });

        String userId = jdbcTemplate.queryForObject(
                "SELECT id FROM aratiri.users WHERE email = ?",
                String.class,
                "owner-pr@example.com"
        );
        assertNotNull(userId);
        OwnerPaymentRequestDTO cancelled = paymentRequestsPort.cancel(userId, publicId);
        assertEquals("CANCELLED", cancelled.getStatus());
        assertFalse(transactionActiveDuringRpc.get(), "cancel must not hold a Spring transaction across LND RPC");
    }

    @Test
    @DisplayName("Settlement marks request PAID even after cancel race; duplicate settlement is safe")
    void settlement_to_paid_wins_cancel_race_and_is_idempotent() {
        Map<?, ?> created = webTestClient().post().uri("/v1/payment-requests")
                .header("Authorization", "Bearer " + ownerToken)
                .header("Idempotency-Key", "settle-race-1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createBody(2200, "Race", 600))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult().getResponseBody();
        assertNotNull(created);
        String publicId = (String) created.get("public_id");

        webTestClient().post().uri("/v1/payment-requests/" + publicId + "/cancel")
                .header("Authorization", "Bearer " + ownerToken)
                .exchange()
                .expectStatus().isOk();

        PaymentRequestEntity cancelled = paymentRequestRepository.findByPublicId(publicId).orElseThrow();
        assertEquals("CANCELLED", cancelled.getStatus());

        settleLinkedInvoice(cancelled);
        PaymentRequestEntity paid = paymentRequestRepository.findByPublicId(publicId).orElseThrow();
        assertEquals("PAID", paid.getStatus());
        assertNotNull(paid.getPaidAt());

        settleLinkedInvoice(paid);
        PaymentRequestEntity stillPaid = paymentRequestRepository.findByPublicId(publicId).orElseThrow();
        assertEquals("PAID", stillPaid.getStatus());
        assertEquals(paid.getPaidAt(), stillPaid.getPaidAt());

        webTestClient().get().uri("/r/" + publicId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("PAID")
                .jsonPath("$.payment_request").doesNotExist();
    }

    @Test
    @DisplayName("Derived EXPIRED status via clock on public and owner views")
    void expiry_derived_status() {
        Map<?, ?> created = webTestClient().post().uri("/v1/payment-requests")
                .header("Authorization", "Bearer " + ownerToken)
                .header("Idempotency-Key", "expire-1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createBody(900, "Expire", 60))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult().getResponseBody();
        assertNotNull(created);
        String publicId = (String) created.get("public_id");

        PaymentRequestEntity entity = paymentRequestRepository.findByPublicId(publicId).orElseThrow();
        entity.setExpiresAt(Instant.now().minusSeconds(5));
        paymentRequestRepository.save(entity);

        webTestClient().get().uri("/r/" + publicId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("EXPIRED")
                .jsonPath("$.payment_request").doesNotExist();

        webTestClient().get().uri("/v1/payment-requests/" + publicId)
                .header("Authorization", "Bearer " + ownerToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("EXPIRED");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Concurrent identical owner/key creates mint one Lightning invoice")
    void concurrent_identical_creates_mint_one_invoice() throws Exception {
        String userId = jdbcTemplate.queryForObject(
                "SELECT id FROM aratiri.users WHERE email = ?",
                String.class,
                "owner-pr@example.com"
        );
        assertNotNull(userId);

        AtomicInteger lndCreates = new AtomicInteger();
        CountDownLatch firstEnteredMint = new CountDownLatch(1);
        CountDownLatch releaseMint = new CountDownLatch(1);
        when(lightningNodePort.createInvoice(anyLong(), anyString(), any(byte[].class), any(byte[].class), anyLong()))
                .thenAnswer(invocation -> {
                    lndCreates.incrementAndGet();
                    firstEnteredMint.countDown();
                    assertTrue(releaseMint.await(10, TimeUnit.SECONDS));
                    long amount = invocation.getArgument(0);
                    long expiry = invocation.getArgument(4);
                    byte[] hash = invocation.getArgument(3);
                    String paymentHash = bytesToHex(hash);
                    return new LightningInvoiceCreation("lnbc" + amount + "req" + paymentHash, paymentHash, expiry);
                });

        CreatePaymentRequestDTO body = createBody(1800, "Concurrent", 900);
        int threadCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        try {
            CountDownLatch start = new CountDownLatch(1);
            CyclicBarrier readyToCreate = new CyclicBarrier(threadCount);
            CountDownLatch done = new CountDownLatch(threadCount);
            List<OwnerPaymentRequestDTO> successes = new ArrayList<>();
            List<Throwable> errors = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        start.await(10, TimeUnit.SECONDS);
                        readyToCreate.await(10, TimeUnit.SECONDS);
                        OwnerPaymentRequestDTO created = paymentRequestsPort.create(userId, "concurrent-idem-1", body);
                        synchronized (successes) {
                            successes.add(created);
                        }
                    } catch (Throwable t) {
                        synchronized (errors) {
                            errors.add(t);
                        }
                    } finally {
                        done.countDown();
                    }
                });
            }

            try {
                start.countDown();
                assertTrue(firstEnteredMint.await(10, TimeUnit.SECONDS), "winner never reached invoice mint");
                int maxPoolWaiters = hikariMaximumPoolSize() - 1;
                awaitAdvisoryLockWaiters(Math.min(threadCount - 1, maxPoolWaiters), 10, TimeUnit.SECONDS);
            } finally {
                releaseMint.countDown();
            }
            assertTrue(done.await(45, TimeUnit.SECONDS), "concurrent creates timed out");

            assertTrue(errors.isEmpty(), () -> "Unexpected errors: " + errors);
            assertEquals(threadCount, successes.size());
            String publicId = successes.getFirst().getPublicId();
            assertTrue(successes.stream().allMatch(dto -> publicId.equals(dto.getPublicId())));
            assertEquals(1, lndCreates.get(), "only one Lightning invoice must be minted");
            assertEquals(1, paymentRequestRepository.count());
        } finally {
            executor.shutdownNow();
            try {
                assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS), "executor did not terminate");
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
                fail("interrupted while awaiting executor termination");
            }
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Concurrent conflicting payloads for same key yield one create and one conflict")
    void concurrent_conflicting_payloads_one_create_one_conflict() throws Exception {
        String userId = jdbcTemplate.queryForObject(
                "SELECT id FROM aratiri.users WHERE email = ?",
                String.class,
                "owner-pr@example.com"
        );
        assertNotNull(userId);

        AtomicInteger lndCreates = new AtomicInteger();
        CountDownLatch firstEnteredMint = new CountDownLatch(1);
        CountDownLatch releaseMint = new CountDownLatch(1);
        when(lightningNodePort.createInvoice(anyLong(), anyString(), any(byte[].class), any(byte[].class), anyLong()))
                .thenAnswer(invocation -> {
                    lndCreates.incrementAndGet();
                    firstEnteredMint.countDown();
                    assertTrue(releaseMint.await(10, TimeUnit.SECONDS));
                    long amount = invocation.getArgument(0);
                    long expiry = invocation.getArgument(4);
                    byte[] hash = invocation.getArgument(3);
                    String paymentHash = bytesToHex(hash);
                    return new LightningInvoiceCreation("lnbc" + amount + "req" + paymentHash, paymentHash, expiry);
                });

        CreatePaymentRequestDTO firstBody = createBody(1900, "A", 900);
        CreatePaymentRequestDTO conflictBody = createBody(2900, "B", 900);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch done = new CountDownLatch(2);
            AtomicInteger createdCount = new AtomicInteger();
            AtomicInteger conflictCount = new AtomicInteger();
            List<Throwable> unexpected = new ArrayList<>();

            executor.submit(() -> {
                try {
                    paymentRequestsPort.create(userId, "concurrent-conflict-1", firstBody);
                    createdCount.incrementAndGet();
                } catch (Throwable t) {
                    synchronized (unexpected) {
                        unexpected.add(t);
                    }
                } finally {
                    done.countDown();
                }
            });

            try {
                assertTrue(firstEnteredMint.await(10, TimeUnit.SECONDS), "winner never reached invoice mint");
                executor.submit(() -> {
                    try {
                        paymentRequestsPort.create(userId, "concurrent-conflict-1", conflictBody);
                        createdCount.incrementAndGet();
                    } catch (PaymentRequestConflictException _) {
                        conflictCount.incrementAndGet();
                    } catch (Throwable t) {
                        synchronized (unexpected) {
                            unexpected.add(t);
                        }
                    } finally {
                        done.countDown();
                    }
                });
                awaitAdvisoryLockWaiters(1, 10, TimeUnit.SECONDS);
            } finally {
                releaseMint.countDown();
            }

            assertTrue(done.await(45, TimeUnit.SECONDS));

            assertTrue(unexpected.isEmpty(), () -> "Unexpected errors: " + unexpected);
            assertEquals(1, createdCount.get());
            assertEquals(1, conflictCount.get());
            assertEquals(1, lndCreates.get());
            assertEquals(1, paymentRequestRepository.count());
        } finally {
            executor.shutdownNow();
            try {
                assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS), "executor did not terminate");
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
                fail("interrupted while awaiting executor termination");
            }
        }
    }

    private int hikariMaximumPoolSize() {
        assertNotNull(jdbcTemplate.getDataSource());
        try {
            return jdbcTemplate.getDataSource().unwrap(HikariDataSource.class).getMaximumPoolSize();
        } catch (Exception e) {
            throw new IllegalStateException("Expected HikariDataSource for pool-size-aware concurrency waits", e);
        }
    }

    private void awaitAdvisoryLockWaiters(int minWaiters, long timeout, TimeUnit unit) throws Exception {
        assertNotNull(jdbcTemplate.getDataSource());
        HikariDataSource hikari = jdbcTemplate.getDataSource().unwrap(HikariDataSource.class);
        long deadlineNanos = System.nanoTime() + unit.toNanos(timeout);
        final long pollBackoffNanos = TimeUnit.MILLISECONDS.toNanos(10);
        // Bypass the app pool so waiter observation cannot deadlock against held create transactions.
        try (Connection connection = DriverManager.getConnection(
                hikari.getJdbcUrl(), hikari.getUsername(), hikari.getPassword());
             Statement statement = connection.createStatement()) {
            while (System.nanoTime() < deadlineNanos) {
                try (ResultSet rs = statement.executeQuery(
                        "SELECT COUNT(*) FROM pg_locks WHERE locktype = 'advisory' AND NOT granted")) {
                    assertTrue(rs.next());
                    if (rs.getInt(1) >= minWaiters) {
                        return;
                    }
                }
                long remainingNanos = deadlineNanos - System.nanoTime();
                if (remainingNanos <= 0) {
                    break;
                }
                LockSupport.parkNanos(Math.min(pollBackoffNanos, remainingNanos));
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("Interrupted while waiting for advisory lock waiters");
                }
            }
        }
        fail("Timed out waiting for at least " + minWaiters + " advisory lock waiter(s)");
    }

    private void settleLinkedInvoice(PaymentRequestEntity entity) {
        invoiceSettlementPort.recordInvoiceStateUpdate(new InvoiceStateUpdate(
                entity.getPaymentRequest(),
                InvoiceStateUpdate.State.SETTLED,
                entity.getAmountSats()
        ));
    }

    private String registerAndVerify(String email, String name, String alias) {
        String password = "SecurePass123!";
        webTestClient().post().uri("/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(registration(name, email, password, alias))
                .exchange()
                .expectStatus().isCreated();

        String code = verificationDataRepository.findById(email).orElseThrow().getCode();
        AuthResponseDTO tokens = webTestClient().post().uri("/v1/auth/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(verification(email, code))
                .exchange()
                .expectStatus().isOk()
                .expectBody(AuthResponseDTO.class)
                .returnResult().getResponseBody();
        assertNotNull(tokens);
        return tokens.getAccessToken();
    }

    private static CreatePaymentRequestDTO createBody(long amount, String memo, long expiresInSeconds) {
        CreatePaymentRequestDTO dto = new CreatePaymentRequestDTO();
        dto.setAmountSats(amount);
        dto.setMemo(memo);
        dto.setExpiresInSeconds(expiresInSeconds);
        return dto;
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

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}

@TestPropertySource(properties = {
        "aratiri.security.auth-rate-limit.enabled=true",
        "aratiri.security.auth-rate-limit.requests-per-window=2",
        "aratiri.security.auth-rate-limit.window=1m",
        "aratiri.security.auth-rate-limit.maximum-keys=1000"
})
class PaymentRequestPublicRateLimitIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean
    private EmailNotificationPort emailNotificationPort;

    @MockitoBean
    private CurrencyConversionPort currencyConversionPort;

    @MockitoBean
    private LightningAddressPort lightningAddressPort;

    @Test
    @DisplayName("Public payment request GET is rate limited")
    void public_get_rate_limited() {
        webTestClient().get().uri("/r/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
                .exchange()
                .expectStatus().value(status -> assertNotEquals(429, status));
        webTestClient().get().uri("/r/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb")
                .exchange()
                .expectStatus().value(status -> assertNotEquals(429, status));
        webTestClient().get().uri("/r/cccccccccccccccccccccccccccccccc")
                .exchange()
                .expectStatus().isEqualTo(429)
                .expectHeader().exists(HttpHeaders.RETRY_AFTER);
    }
}
