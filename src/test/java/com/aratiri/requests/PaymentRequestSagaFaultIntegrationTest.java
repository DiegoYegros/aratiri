package com.aratiri.requests;

import com.aratiri.AbstractIntegrationTest;
import com.aratiri.accounts.application.port.out.CurrencyConversionPort;
import com.aratiri.accounts.application.port.out.LightningAddressPort;
import com.aratiri.auth.application.dto.AuthResponseDTO;
import com.aratiri.auth.application.dto.RegistrationRequestDTO;
import com.aratiri.auth.application.dto.VerificationRequestDTO;
import com.aratiri.auth.application.port.out.EmailNotificationPort;
import com.aratiri.infrastructure.messaging.KafkaTopics;
import com.aratiri.infrastructure.persistence.jpa.entity.AccountEntryEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.OutboxEventEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.PaymentRequestEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.TransactionEntity;
import com.aratiri.infrastructure.persistence.jpa.repository.AccountEntryRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.OutboxEventRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.PaymentRequestRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.TransactionsRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.VerificationDataRepository;
import com.aratiri.invoices.application.InvoiceStateUpdate;
import com.aratiri.invoices.application.event.InvoiceSettledEvent;
import com.aratiri.invoices.application.port.in.InvoiceSettlementPort;
import com.aratiri.invoices.application.port.out.LightningNodePort;
import com.aratiri.invoices.domain.InvoiceCancelOutcome;
import com.aratiri.invoices.domain.LightningInvoice;
import com.aratiri.invoices.domain.LightningInvoiceCreation;
import com.aratiri.invoices.domain.LightningNodeInvoice;
import com.aratiri.payments.application.invoice.InvoiceProcessorService;
import com.aratiri.payments.domain.LightningInvoiceUpdate;
import com.aratiri.requests.application.PaymentRequestSagaService;
import com.aratiri.requests.application.dto.CreatePaymentRequestDTO;
import com.aratiri.requests.application.port.in.PaymentRequestsPort;
import com.aratiri.transactions.application.InvoiceCreditSettlement;
import com.aratiri.transactions.application.TransactionSettlementModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

class PaymentRequestSagaFaultIntegrationTest extends AbstractIntegrationTest {

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
    private PaymentRequestSagaService sagaService;
    @Autowired
    private PaymentRequestsPort paymentRequestsPort;
    @Autowired
    private com.aratiri.requests.application.port.out.PaymentRequestPersistencePort persistencePort;
    @Autowired
    private com.aratiri.requests.application.PaymentRequestProvisioningFinalizer provisioningFinalizer;
    @Autowired
    private com.aratiri.infrastructure.persistence.jpa.repository.LightningInvoiceRepository lightningInvoiceRepository;
    @Autowired
    private InvoiceSettlementPort invoiceSettlementPort;
    @Autowired
    private InvoiceProcessorService invoiceProcessorService;
    @Autowired
    private OutboxEventRepository outboxEventRepository;
    @Autowired
    private TransactionsRepository transactionRepository;
    @Autowired
    private AccountEntryRepository accountEntryRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private com.aratiri.infrastructure.messaging.outbox.OutboxWriter outboxWriter;
    @Autowired
    private TransactionSettlementModule transactionSettlementModule;

    private String ownerToken;
    private final java.util.concurrent.ConcurrentHashMap<String, LightningNodeInvoice> lndInvoices =
            new java.util.concurrent.ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        lndInvoices.clear();
        when(currencyConversionPort.getCurrentBtcPrice()).thenReturn(Map.of("usd", BigDecimal.valueOf(50000)));
        when(lightningAddressPort.generateTaprootAddress()).thenAnswer(invocation ->
                "bc1p_pr_fault_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        doAnswer(invocation -> null).when(emailNotificationPort).sendVerificationEmail(anyString(), anyString());
        stubHappyLnd();
        ownerToken = registerAndVerify("owner-saga-fault@example.com", "Owner Saga Fault", "ownersagafault");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Accepted-but-timeout AddInvoice recovers via lookup with a single mint")
    void provision_timeoutThenLookupRecoversSingleMint() {
        AtomicInteger creates = new AtomicInteger();
        when(lightningNodePort.createInvoice(anyLong(), anyString(), any(byte[].class), any(byte[].class), anyLong()))
                .thenAnswer(invocation -> {
                    int n = creates.incrementAndGet();
                    long amount = invocation.getArgument(0);
                    long expiry = invocation.getArgument(4);
                    byte[] hash = invocation.getArgument(3);
                    String paymentHash = bytesToHex(hash);
                    String bolt11 = "lnbc" + amount + "req" + paymentHash;
                    lndInvoices.put(paymentHash, new LightningNodeInvoice(
                            bolt11, LightningInvoice.InvoiceState.OPEN, 0L, amount));
                    if (n == 1) {
                        throw new RuntimeException("timeout after accept");
                    }
                    return new LightningInvoiceCreation(bolt11, paymentHash, expiry);
                });

        // Persist intent without completing provision by forcing create failure on request thread.
        Map<?, ?> created = webTestClient().post().uri("/v1/payment-requests")
                .header("Authorization", "Bearer " + ownerToken)
                .header("Idempotency-Key", "fault-timeout-1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createBody(2100, "Timeout recover", 900))
                .exchange()
                .expectStatus().isAccepted()
                .expectBody(Map.class)
                .returnResult().getResponseBody();
        assertNotNull(created);
        String publicId = (String) created.get("public_id");
        PaymentRequestEntity pending = paymentRequestRepository.findByPublicId(publicId).orElseThrow();
        assertEquals("PROVISIONING", pending.getStatus());
        assertEquals(1, creates.get());
        assertTrue(lndInvoices.containsKey(pending.getPaymentHash()));

        // Make the row due now (first failure scheduled backoff).
        jdbcTemplate.update("""
                UPDATE aratiri.payment_requests
                SET provision_next_attempt_at = NOW() - INTERVAL '1 second',
                    provision_locked_until = NULL,
                    provision_locked_by = NULL
                WHERE id = ?
                """, pending.getId());

        // Worker recovers by lookup — must not mint again.
        sagaService.tryProvision(pending.getId());

        PaymentRequestEntity open = paymentRequestRepository.findByPublicId(publicId).orElseThrow();
        assertEquals("OPEN", open.getStatus());
        assertNotNull(open.getPaymentRequest());
        assertEquals(1, creates.get(), "lookup recovery must not mint a second invoice");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Failure after LND success before local finalization is healed by worker retry")
    void provision_lndSuccessLocalFinalizeFailureThenRetry() {
        AtomicInteger creates = new AtomicInteger();
        when(lightningNodePort.createInvoice(anyLong(), anyString(), any(byte[].class), any(byte[].class), anyLong()))
                .thenAnswer(invocation -> {
                    creates.incrementAndGet();
                    long amount = invocation.getArgument(0);
                    long expiry = invocation.getArgument(4);
                    byte[] hash = invocation.getArgument(3);
                    String paymentHash = bytesToHex(hash);
                    String bolt11 = "lnbc" + amount + "req" + paymentHash;
                    lndInvoices.put(paymentHash, new LightningNodeInvoice(
                            bolt11, LightningInvoice.InvoiceState.OPEN, 0L, amount));
                    return new LightningInvoiceCreation(bolt11, paymentHash, expiry);
                });

        Map<?, ?> created = webTestClient().post().uri("/v1/payment-requests")
                .header("Authorization", "Bearer " + ownerToken)
                .header("Idempotency-Key", "fault-finalize-1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createBody(2200, "Finalize heal", 900))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult().getResponseBody();
        assertNotNull(created);
        String publicId = (String) created.get("public_id");
        PaymentRequestEntity entity = paymentRequestRepository.findByPublicId(publicId).orElseThrow();

        // Simulate crash after LND mint: roll request back to PROVISIONING without BOLT11 locally.
        jdbcTemplate.update("""
                UPDATE aratiri.payment_requests
                SET status = 'PROVISIONING',
                    payment_request = NULL,
                    invoice_id = NULL,
                    provision_next_attempt_at = NOW() - INTERVAL '1 second',
                    provision_locked_until = NULL,
                    provision_locked_by = NULL
                WHERE id = ?
                """, entity.getId());
        // Keep LND invoice present for lookup recovery.
        assertTrue(lndInvoices.containsKey(entity.getPaymentHash()));

        sagaService.tryProvision(entity.getId());

        PaymentRequestEntity healed = paymentRequestRepository.findByPublicId(publicId).orElseThrow();
        assertEquals("OPEN", healed.getStatus());
        assertNotNull(healed.getPaymentRequest());
        assertEquals(1, creates.get(), "recovery must reuse existing LND invoice");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Cancel while provision paused between lookup and AddInvoice fences hash before CANCELLED")
    void cancel_whileProvisionPausedBetweenLookupAndAdd_fencesHash() throws Exception {
        CountDownLatch lookupStarted = new CountDownLatch(1);
        CountDownLatch allowAddInvoice = new CountDownLatch(1);
        AtomicInteger creates = new AtomicInteger();
        AtomicBoolean blockFirstEmptyLookup = new AtomicBoolean(true);

        when(lightningNodePort.lookupInvoice(anyString())).thenAnswer(invocation -> {
            String hash = invocation.getArgument(0);
            LightningNodeInvoice existing = lndInvoices.get(hash);
            if (existing == null && blockFirstEmptyLookup.compareAndSet(true, false)) {
                lookupStarted.countDown();
                assertTrue(allowAddInvoice.await(15, TimeUnit.SECONDS), "cancel did not release provisioner");
            }
            return Optional.ofNullable(lndInvoices.get(hash));
        });
        when(lightningNodePort.createInvoice(anyLong(), anyString(), any(byte[].class), any(byte[].class), anyLong()))
                .thenAnswer(invocation -> {
                    creates.incrementAndGet();
                    long amount = invocation.getArgument(0);
                    long expiry = invocation.getArgument(4);
                    byte[] hash = invocation.getArgument(3);
                    String paymentHash = bytesToHex(hash);
                    String bolt11 = "lnbc" + amount + "req" + paymentHash;
                    // If cancel already fenced/canceled this hash, still record presence.
                    LightningNodeInvoice current = lndInvoices.get(paymentHash);
                    if (current != null && current.state() == LightningInvoice.InvoiceState.CANCELED) {
                        throw new RuntimeException("invoice with payment hash already exists");
                    }
                    lndInvoices.put(paymentHash, new LightningNodeInvoice(
                            bolt11, LightningInvoice.InvoiceState.OPEN, 0L, amount));
                    return new LightningInvoiceCreation(bolt11, paymentHash, expiry);
                });

        // Commit PROVISIONING intent without completing provision: block first create path via lookup wait.
        // First force intent-only by making immediate tryProvision hang; use direct port create with blocked mock.
        String userId = jdbcTemplate.queryForObject(
                "SELECT id FROM aratiri.users WHERE email = ?", String.class, "owner-saga-fault@example.com");
        assertNotNull(userId);

        Thread provisioner = new Thread(() -> paymentRequestsPort.create(userId, "fault-cancel-race-1", createBody(2300, "Race", 900)),
                "provisioner-race");
        provisioner.start();
        assertTrue(lookupStarted.await(15, TimeUnit.SECONDS), "provisioner never reached lookup");

        PaymentRequestEntity provisioning = paymentRequestRepository.findAll().stream()
                .filter(r -> "fault-cancel-race-1".equals(r.getIdempotencyKey()))
                .findFirst()
                .orElseThrow();
        assertEquals("PROVISIONING", provisioning.getStatus());

        // Cancel while provisioner is between lookup and AddInvoice.
        webTestClient().post().uri("/v1/payment-requests/" + provisioning.getPublicId() + "/cancel")
                .header("Authorization", "Bearer " + ownerToken)
                .exchange()
                .expectStatus().value(status -> assertTrue(status == 200 || status == 202));

        PaymentRequestEntity afterCancel = paymentRequestRepository.findById(provisioning.getId()).orElseThrow();
        assertTrue(
                afterCancel.getStatus().equals("CANCEL_PENDING") || afterCancel.getStatus().equals("CANCELLED"),
                "cancel must leave non-payable status, was " + afterCancel.getStatus()
        );
        assertNull(afterCancel.getPaymentRequest(), "BOLT11 must not be exposed after cancel");

        // Release stale provisioner; AddInvoice for same hash must not leave a payable OPEN request.
        allowAddInvoice.countDown();
        provisioner.join(15_000);
        assertFalse(provisioner.isAlive(), "provisioner did not finish");

        // Drive cancel saga to completion if still pending.
        for (int i = 0; i < 5; i++) {
            PaymentRequestEntity current = paymentRequestRepository.findById(provisioning.getId()).orElseThrow();
            if ("CANCELLED".equals(current.getStatus()) || "PAID".equals(current.getStatus())) {
                break;
            }
            // Expire cancel lease if held
            jdbcTemplate.update("""
                    UPDATE aratiri.payment_requests
                    SET cancel_locked_until = NOW() - INTERVAL '1 second',
                        cancel_next_attempt_at = NOW() - INTERVAL '1 second'
                    WHERE id = ? AND status = 'CANCEL_PENDING'
                    """, provisioning.getId());
            sagaService.tryCancel(provisioning.getId());
        }

        PaymentRequestEntity finalEntity = paymentRequestRepository.findById(provisioning.getId()).orElseThrow();
        assertEquals("CANCELLED", finalEntity.getStatus());
        assertNull(finalEntity.getPaymentRequest());
        LightningNodeInvoice node = lndInvoices.get(finalEntity.getPaymentHash());
        assertNotNull(node, "cancel fence must ensure deterministic invoice exists");
        assertEquals(LightningInvoice.InvoiceState.CANCELED, node.state());
        assertTrue(creates.get() >= 1, "hash must be materialized at least once for fencing");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Crash after LND cancel before DB finalize is healed by cancel worker retry")
    void cancel_crashAfterLndCancelBeforeDbFinalize_retriesToCancelled() {
        Map<?, ?> created = webTestClient().post().uri("/v1/payment-requests")
                .header("Authorization", "Bearer " + ownerToken)
                .header("Idempotency-Key", "fault-cancel-crash-1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createBody(2400, "Cancel crash", 900))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult().getResponseBody();
        assertNotNull(created);
        String publicId = (String) created.get("public_id");
        PaymentRequestEntity open = paymentRequestRepository.findByPublicId(publicId).orElseThrow();

        // Simulate CANCEL_PENDING after LND already canceled, before DB finalize.
        LightningNodeInvoice node = lndInvoices.get(open.getPaymentHash());
        lndInvoices.put(open.getPaymentHash(), new LightningNodeInvoice(
                node.paymentRequest(), LightningInvoice.InvoiceState.CANCELED, 0L, node.valueSats()));
        jdbcTemplate.update("""
                UPDATE aratiri.payment_requests
                SET status = 'CANCEL_PENDING',
                    cancel_next_attempt_at = NOW() - INTERVAL '1 second',
                    cancel_locked_until = NULL,
                    cancel_locked_by = NULL,
                    payment_request = payment_request
                WHERE id = ?
                """, open.getId());

        sagaService.tryCancel(open.getId());

        PaymentRequestEntity cancelled = paymentRequestRepository.findByPublicId(publicId).orElseThrow();
        assertEquals("CANCELLED", cancelled.getStatus());
        assertNotNull(cancelled.getCancelledAt());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Admin FAILED requeue is conditional and does not overwrite PAID")
    void adminRequeue_doesNotOverwritePaid() {
        Map<?, ?> created = webTestClient().post().uri("/v1/payment-requests")
                .header("Authorization", "Bearer " + ownerToken)
                .header("Idempotency-Key", "fault-requeue-paid-1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createBody(2550, "Requeue paid fence", 900))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult().getResponseBody();
        assertNotNull(created);
        String publicId = (String) created.get("public_id");
        PaymentRequestEntity entity = paymentRequestRepository.findByPublicId(publicId).orElseThrow();

        jdbcTemplate.update("""
                UPDATE aratiri.payment_requests
                SET status = 'FAILED',
                    provision_attempt_count = 3,
                    provision_last_error = 'exhausted',
                    provision_next_attempt_at = NULL,
                    provision_locked_by = NULL,
                    provision_locked_until = NULL
                WHERE id = ?
                """, entity.getId());

        Instant now = Instant.now();
        assertEquals(1, persistencePort.requeueFailedProvisioning(publicId, now));
        PaymentRequestEntity requeued = paymentRequestRepository.findByPublicId(publicId).orElseThrow();
        assertEquals("PROVISIONING", requeued.getStatus());
        assertEquals(0, requeued.getProvisionAttemptCount());
        assertNull(requeued.getProvisionLastError());
        assertNotNull(requeued.getProvisionNextAttemptAt());

        jdbcTemplate.update("""
                UPDATE aratiri.payment_requests
                SET status = 'PAID',
                    paid_at = NOW(),
                    provision_attempt_count = 2,
                    provision_next_attempt_at = NULL,
                    provision_locked_by = NULL,
                    provision_locked_until = NULL
                WHERE id = ?
                """, entity.getId());

        assertEquals(0, persistencePort.requeueFailedProvisioning(publicId, now));
        PaymentRequestEntity stillPaid = paymentRequestRepository.findByPublicId(publicId).orElseThrow();
        assertEquals("PAID", stillPaid.getStatus());
        assertEquals(2, stillPaid.getProvisionAttemptCount());
        assertNotNull(stillPaid.getPaidAt());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Stale OPEN finalize after cancel does not materialize local invoice")
    void finalizeOpen_afterCancel_skipsLocalInvoiceUpsert() {
        Map<?, ?> created = webTestClient().post().uri("/v1/payment-requests")
                .header("Authorization", "Bearer " + ownerToken)
                .header("Idempotency-Key", "fault-stale-open-finalize-1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createBody(2575, "Stale open finalize", 900))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult().getResponseBody();
        assertNotNull(created);
        PaymentRequestEntity entity = paymentRequestRepository.findByPublicId((String) created.get("public_id")).orElseThrow();

        // Wipe local invoice so upsert would create one if the fence failed.
        if (entity.getInvoiceId() != null) {
            jdbcTemplate.update("DELETE FROM aratiri.lightning_invoices WHERE id = ?", entity.getInvoiceId());
        }
        jdbcTemplate.update("""
                UPDATE aratiri.payment_requests
                SET status = 'PROVISIONING',
                    payment_request = NULL,
                    invoice_id = NULL,
                    provision_attempt_count = 1,
                    provision_next_attempt_at = NOW() - INTERVAL '1 second',
                    provision_locked_by = 'request-saga-stale-open',
                    provision_locked_until = NOW() + INTERVAL '5 minutes',
                    provision_last_error = NULL
                WHERE id = ?
                """, entity.getId());

        Instant now = Instant.now();
        assertEquals(1, persistencePort.markCancelPendingIfPayable(
                entity.getPublicId(), entity.getUserId(), now));
        PaymentRequestEntity cancelPending = paymentRequestRepository.findById(entity.getId()).orElseThrow();
        assertEquals("CANCEL_PENDING", cancelPending.getStatus());
        assertNull(cancelPending.getProvisionLockedBy());

        var domain = persistencePort.findById(entity.getId()).orElseThrow();
        provisioningFinalizer.finalizeOpenOrSettled(
                domain,
                "lnbc-stale-open",
                LightningInvoice.InvoiceState.OPEN,
                0L,
                900L,
                "request-saga-stale-open"
        );

        assertTrue(lightningInvoiceRepository.findByPaymentHash(entity.getPaymentHash()).isEmpty(),
                "stale OPEN finalize must not upsert a local lightning invoice");
        PaymentRequestEntity after = paymentRequestRepository.findById(entity.getId()).orElseThrow();
        assertEquals("CANCEL_PENDING", after.getStatus());
        assertNull(after.getPaymentRequest());
        assertNull(after.getInvoiceId());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Stale provision worker cannot overwrite a newer claim after lease expiry")
    void provision_staleWorkerCannotOverwriteNewerClaim() {
        Map<?, ?> created = webTestClient().post().uri("/v1/payment-requests")
                .header("Authorization", "Bearer " + ownerToken)
                .header("Idempotency-Key", "fault-lease-1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createBody(2500, "Lease fence", 900))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult().getResponseBody();
        assertNotNull(created);
        PaymentRequestEntity entity = paymentRequestRepository.findByPublicId((String) created.get("public_id")).orElseThrow();

        // Force back to PROVISIONING with an active stale claim token.
        jdbcTemplate.update("""
                UPDATE aratiri.payment_requests
                SET status = 'PROVISIONING',
                    payment_request = NULL,
                    invoice_id = NULL,
                    provision_attempt_count = 1,
                    provision_next_attempt_at = NOW() - INTERVAL '1 second',
                    provision_locked_by = 'request-saga-stale-token',
                    provision_locked_until = NOW() - INTERVAL '1 second',
                    provision_last_error = NULL
                WHERE id = ?
                """, entity.getId());

        Instant now = Instant.now();
        int claimed = persistencePort.claimProvisioning(
                entity.getId(), "request-saga-new-token", now.plusSeconds(300), now);
        assertEquals(1, claimed);

        // Stale completion/retry must not clear the new claim.
        assertEquals(0, persistencePort.scheduleProvisioningRetry(
                entity.getId(), "stale failure", now.plusSeconds(60), "request-saga-stale-token"));
        assertEquals(0, persistencePort.finalizeProvisioningOpen(
                entity.getId(), "lnbc-stale", "inv-stale", "request-saga-stale-token"));
        assertEquals(0, persistencePort.markProvisioningFailed(
                entity.getId(), "stale fail", "request-saga-stale-token"));

        PaymentRequestEntity stillClaimed = paymentRequestRepository.findById(entity.getId()).orElseThrow();
        assertEquals("PROVISIONING", stillClaimed.getStatus());
        assertEquals("request-saga-new-token", stillClaimed.getProvisionLockedBy());
        assertNull(stillClaimed.getPaymentRequest());

        assertEquals(1, persistencePort.finalizeProvisioningOpen(
                entity.getId(), "lnbc-new", "inv-new", "request-saga-new-token"));
        PaymentRequestEntity opened = paymentRequestRepository.findById(entity.getId()).orElseThrow();
        assertEquals("OPEN", opened.getStatus());
        assertEquals("lnbc-new", opened.getPaymentRequest());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Stale cancel worker cannot overwrite a newer cancel claim after lease expiry")
    void cancel_staleWorkerCannotOverwriteNewerClaim() {
        Map<?, ?> created = webTestClient().post().uri("/v1/payment-requests")
                .header("Authorization", "Bearer " + ownerToken)
                .header("Idempotency-Key", "fault-lease-cancel-1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createBody(2600, "Cancel lease", 900))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult().getResponseBody();
        assertNotNull(created);
        PaymentRequestEntity entity = paymentRequestRepository.findByPublicId((String) created.get("public_id")).orElseThrow();

        jdbcTemplate.update("""
                UPDATE aratiri.payment_requests
                SET status = 'CANCEL_PENDING',
                    cancel_attempt_count = 1,
                    cancel_next_attempt_at = NOW() - INTERVAL '1 second',
                    cancel_locked_by = 'request-saga-stale-cancel',
                    cancel_locked_until = NOW() - INTERVAL '1 second'
                WHERE id = ?
                """, entity.getId());

        Instant now = Instant.now();
        assertEquals(1, persistencePort.claimCancellation(
                entity.getId(), "request-saga-new-cancel", now.plusSeconds(300), now));

        assertEquals(0, persistencePort.scheduleCancelRetry(
                entity.getId(), "stale", now.plusSeconds(30), "request-saga-stale-cancel"));
        assertEquals(0, persistencePort.finalizeCancelled(
                entity.getId(), now, "request-saga-stale-cancel"));

        PaymentRequestEntity still = paymentRequestRepository.findById(entity.getId()).orElseThrow();
        assertEquals("CANCEL_PENDING", still.getStatus());
        assertEquals("request-saga-new-cancel", still.getCancelLockedBy());

        assertEquals(1, persistencePort.finalizeCancelled(
                entity.getId(), now, "request-saga-new-cancel"));
        assertEquals("CANCELLED", paymentRequestRepository.findById(entity.getId()).orElseThrow().getStatus());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Duplicate settlement/recovery paths produce one outbox event, one credit tx, one ledger entry")
    void duplicateSettlementPaths_singleOutboxCreditAndLedger() {
        Map<?, ?> created = webTestClient().post().uri("/v1/payment-requests")
                .header("Authorization", "Bearer " + ownerToken)
                .header("Idempotency-Key", "fault-dup-settle-1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createBody(2700, "Dup settle", 900))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult().getResponseBody();
        assertNotNull(created);
        PaymentRequestEntity entity = paymentRequestRepository.findByPublicId((String) created.get("public_id")).orElseThrow();

        LightningInvoiceUpdate update = new LightningInvoiceUpdate(
                entity.getPaymentRequest(),
                entity.getPaymentHash(),
                LightningInvoiceUpdate.State.SETTLED,
                entity.getAmountSats(),
                100L,
                200L
        );
        invoiceProcessorService.processInvoiceUpdate(update);
        // Recovery / replay paths
        invoiceSettlementPort.recordInvoiceStateUpdate(new InvoiceStateUpdate(
                entity.getPaymentRequest(),
                entity.getPaymentHash(),
                InvoiceStateUpdate.State.SETTLED,
                entity.getAmountSats()
        ));
        outboxWriter.publishInvoiceSettled(
                entity.getInvoiceId(),
                new InvoiceSettledEvent(
                        entity.getUserId(),
                        entity.getAmountSats(),
                        entity.getPaymentHash(),
                        LocalDateTime.now(),
                        entity.getMemo()
                )
        );
        invoiceProcessorService.processInvoiceUpdate(update);

        List<OutboxEventEntity> settledOutbox = outboxEventRepository.findAll().stream()
                .filter(e -> "Invoice".equals(e.getAggregateType()))
                .filter(e -> KafkaTopics.INVOICE_SETTLED.getCode().equals(e.getEventType()))
                .filter(e -> entity.getInvoiceId().equals(e.getAggregateId()))
                .toList();
        assertEquals(1, settledOutbox.size(), "exactly one invoice.settled outbox row");

        transactionSettlementModule.settleInvoiceCredit(new InvoiceCreditSettlement(
                entity.getUserId(),
                entity.getAmountSats(),
                entity.getPaymentHash(),
                entity.getMemo() == null ? "settled" : entity.getMemo(),
                null,
                null
        ));
        transactionSettlementModule.settleInvoiceCredit(new InvoiceCreditSettlement(
                entity.getUserId(),
                entity.getAmountSats(),
                entity.getPaymentHash(),
                entity.getMemo() == null ? "settled" : entity.getMemo(),
                null,
                null
        ));

        List<TransactionEntity> credits = transactionRepository.findAll().stream()
                .filter(tx -> entity.getPaymentHash().equals(tx.getReferenceId()))
                .toList();
        assertEquals(1, credits.size(), "exactly one credit transaction");
        List<AccountEntryEntity> entries = accountEntryRepository.findAll().stream()
                .filter(e -> credits.getFirst().getId().equals(e.getTransactionId()))
                .toList();
        assertEquals(1, entries.size(), "exactly one ledger entry");
        assertEquals("PAID", paymentRequestRepository.findById(entity.getId()).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("Expired provisioning intent converges to FAILED without exposing BOLT11")
    void provision_expiredIntent_terminalFailedNoBolt11() {
        Map<?, ?> created = webTestClient().post().uri("/v1/payment-requests")
                .header("Authorization", "Bearer " + ownerToken)
                .header("Idempotency-Key", "fault-expired-1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createBody(2800, "Expire intent", 900))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult().getResponseBody();
        assertNotNull(created);
        PaymentRequestEntity entity = paymentRequestRepository.findByPublicId((String) created.get("public_id")).orElseThrow();

        jdbcTemplate.update("""
                UPDATE aratiri.payment_requests
                SET status = 'PROVISIONING',
                    payment_request = NULL,
                    invoice_id = NULL,
                    expires_at = NOW() - INTERVAL '5 seconds',
                    provision_next_attempt_at = NOW() - INTERVAL '1 second',
                    provision_locked_until = NULL,
                    provision_locked_by = NULL
                WHERE id = ?
                """, entity.getId());
        lndInvoices.remove(entity.getPaymentHash());

        AtomicInteger creates = new AtomicInteger();
        when(lightningNodePort.createInvoice(anyLong(), anyString(), any(byte[].class), any(byte[].class), anyLong()))
                .thenAnswer(invocation -> {
                    creates.incrementAndGet();
                    throw new AssertionError("must not AddInvoice for expired intent");
                });

        sagaService.tryProvision(entity.getId());

        PaymentRequestEntity failed = paymentRequestRepository.findById(entity.getId()).orElseThrow();
        assertEquals("FAILED", failed.getStatus());
        assertNull(failed.getPaymentRequest());
        assertEquals(0, creates.get());
        assertEquals(PaymentRequestSagaService.EXPIRED_BEFORE_MATERIALIZATION, failed.getProvisionLastError());
    }

    private void stubHappyLnd() {
        when(lightningNodePort.createInvoice(anyLong(), anyString(), any(byte[].class), any(byte[].class), anyLong()))
                .thenAnswer(invocation -> {
                    long amount = invocation.getArgument(0);
                    long expiry = invocation.getArgument(4);
                    byte[] hash = invocation.getArgument(3);
                    String paymentHash = bytesToHex(hash);
                    String bolt11 = "lnbc" + amount + "req" + paymentHash;
                    lndInvoices.put(paymentHash, new LightningNodeInvoice(
                            bolt11, LightningInvoice.InvoiceState.OPEN, 0L, amount));
                    return new LightningInvoiceCreation(bolt11, paymentHash, expiry);
                });
        when(lightningNodePort.lookupInvoice(anyString())).thenAnswer(invocation ->
                Optional.ofNullable(lndInvoices.get(invocation.getArgument(0))));
        when(lightningNodePort.cancelInvoice(anyString())).thenAnswer(invocation -> {
            String hash = invocation.getArgument(0);
            LightningNodeInvoice existing = lndInvoices.get(hash);
            if (existing == null) {
                return InvoiceCancelOutcome.NOT_FOUND;
            }
            if (existing.state() == LightningInvoice.InvoiceState.SETTLED) {
                return InvoiceCancelOutcome.ALREADY_SETTLED;
            }
            lndInvoices.put(hash, new LightningNodeInvoice(
                    existing.paymentRequest(),
                    LightningInvoice.InvoiceState.CANCELED,
                    existing.amountPaidSats(),
                    existing.valueSats()
            ));
            return InvoiceCancelOutcome.CANCELLED;
        });
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
