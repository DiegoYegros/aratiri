package com.aratiri.requests.application;

import com.aratiri.infrastructure.configuration.PaymentRequestSagaProperties;
import com.aratiri.invoices.application.port.out.LightningNodePort;
import com.aratiri.invoices.domain.InvoiceCancelOutcome;
import com.aratiri.invoices.domain.LightningInvoice;
import com.aratiri.invoices.domain.LightningInvoiceCreation;
import com.aratiri.invoices.domain.LightningNodeInvoice;
import com.aratiri.requests.application.port.out.PaymentRequestPersistencePort;
import com.aratiri.requests.domain.PaymentRequest;
import com.aratiri.requests.domain.PaymentRequestStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Leased recovery workers for payment-request provisioning and cancellation.
 * Never holds a DB transaction across LND RPCs.
 */
@Service
public class PaymentRequestSagaService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentRequestSagaService.class);
    private static final String LOCKED_BY_PREFIX = "request-saga-";
    public static final String EXPIRED_BEFORE_MATERIALIZATION =
            "Provisioning intent expired before invoice materialization";

    private final PaymentRequestPersistencePort persistencePort;
    private final LightningNodePort lightningNodePort;
    private final PaymentRequestProvisioningFinalizer provisioningFinalizer;
    private final PaymentRequestSagaProperties properties;
    private final Clock clock;

    public PaymentRequestSagaService(
            PaymentRequestPersistencePort persistencePort,
            LightningNodePort lightningNodePort,
            PaymentRequestProvisioningFinalizer provisioningFinalizer,
            PaymentRequestSagaProperties properties,
            Clock clock
    ) {
        this.persistencePort = persistencePort;
        this.lightningNodePort = lightningNodePort;
        this.provisioningFinalizer = provisioningFinalizer;
        this.properties = properties;
        this.clock = clock;
    }

    public void processDueWork() {
        processDueProvisioning();
        processDueCancellations();
    }

    public void processDueProvisioning() {
        Instant now = clock.instant();
        List<PaymentRequest> due = persistencePort.findDueProvisioning(now, properties.getBatchSize());
        for (PaymentRequest candidate : due) {
            tryProvision(candidate.id());
        }
    }

    public void processDueCancellations() {
        Instant now = clock.instant();
        List<PaymentRequest> due = persistencePort.findDueCancellations(now, properties.getBatchSize());
        for (PaymentRequest candidate : due) {
            tryCancel(candidate.id());
        }
    }

    /**
     * Best-effort immediate provisioning after durable intent commit (request-thread latency path).
     * Recovery remains independent via {@link #processDueProvisioning()}.
     */
    public void tryProvision(String requestId) {
        Instant now = clock.instant();
        Instant lockedUntil = now.plusSeconds(properties.getLeaseSeconds());
        String lockedBy = newClaimToken();
        int claimed = persistencePort.claimProvisioning(requestId, lockedBy, lockedUntil, now);
        if (claimed == 0) {
            return;
        }

        PaymentRequest request = persistencePort.findById(requestId).orElse(null);
        if (request == null || request.storedStatus() != PaymentRequestStatus.PROVISIONING) {
            return;
        }

        try {
            if (!now.isBefore(request.expiresAt())) {
                persistencePort.markProvisioningFailed(request.id(), EXPIRED_BEFORE_MATERIALIZATION, lockedBy);
                logger.info(
                        "Provisioning intent already expired; terminal FAILED without AddInvoice requestId={} paymentHash={}",
                        request.id(),
                        request.paymentHash()
                );
                return;
            }

            long remainingExpirySeconds = remainingExpirySeconds(request, now);
            Optional<LightningNodeInvoice> existing = lightningNodePort.lookupInvoice(request.paymentHash());
            String bolt11;
            LightningInvoice.InvoiceState state;
            long amountPaidSats;
            long expirySeconds = remainingExpirySeconds;

            if (existing.isPresent()) {
                LightningNodeInvoice nodeInvoice = existing.get();
                bolt11 = nodeInvoice.paymentRequest();
                state = nodeInvoice.state();
                amountPaidSats = nodeInvoice.amountPaidSats();
            } else {
                byte[] preimage = Base64.getDecoder().decode(request.preimage());
                byte[] hash = HexFormat.of().parseHex(request.paymentHash());
                LightningInvoiceCreation creation = lightningNodePort.createInvoice(
                        request.amountSats(),
                        request.memo() == null ? "" : request.memo(),
                        preimage,
                        hash,
                        remainingExpirySeconds
                );
                bolt11 = creation.paymentRequest();
                state = LightningInvoice.InvoiceState.OPEN;
                amountPaidSats = 0L;
                expirySeconds = creation.expiry();
            }

            provisioningFinalizer.finalizeOpenOrSettled(
                    request, bolt11, state, amountPaidSats, expirySeconds, lockedBy);
        } catch (Exception e) {
            handleProvisionFailure(request, lockedBy, e);
        }
    }

    public void tryCancel(String requestId) {
        Instant now = clock.instant();
        Instant lockedUntil = now.plusSeconds(properties.getLeaseSeconds());
        String lockedBy = newClaimToken();
        int claimed = persistencePort.claimCancellation(requestId, lockedBy, lockedUntil, now);
        if (claimed == 0) {
            return;
        }

        PaymentRequest request = persistencePort.findById(requestId).orElse(null);
        if (request == null || request.storedStatus() != PaymentRequestStatus.CANCEL_PENDING) {
            return;
        }

        try {
            if (cancelAgainstNode(request, lockedBy, now)) {
                return;
            }
            finalizeCancelOrLogPaidRace(request, lockedBy);
        } catch (Exception e) {
            handleCancelFailure(request, lockedBy, e);
        }
    }

    /**
     * @return true when settlement pipeline handled the request (skip CANCELLED finalize)
     */
    private boolean cancelAgainstNode(PaymentRequest request, String lockedBy, Instant now) {
        Optional<LightningNodeInvoice> existing = lightningNodePort.lookupInvoice(request.paymentHash());
        if (existing.isEmpty()) {
            // Fence the deterministic hash so a stale provision AddInvoice cannot mint a payable orphan.
            ensureDeterministicInvoiceThenCancel(request, now);
            return false;
        }
        LightningNodeInvoice nodeInvoice = existing.get();
        if (nodeInvoice.state() == LightningInvoice.InvoiceState.SETTLED) {
            settleOwnedFromNode(request, nodeInvoice, lockedBy);
            return true;
        }
        return nodeInvoice.state() != LightningInvoice.InvoiceState.CANCELED
                && cancelAndHandleSettledRace(request, lockedBy);
    }

    private void finalizeCancelOrLogPaidRace(PaymentRequest request, String lockedBy) {
        int updated = persistencePort.finalizeCancelled(request.id(), clock.instant(), lockedBy);
        if (updated == 0) {
            PaymentRequest after = persistencePort.findById(request.id()).orElse(null);
            if (after != null && after.storedStatus() == PaymentRequestStatus.PAID) {
                logger.info("Cancel finalize raced with PAID for requestId={}", request.id());
            }
        }
    }

    /**
     * Lookup-first ensure of the durable payment hash on LND, then cancel/verify.
     * Guarantees a later AddInvoice for the same hash cannot create a new payable invoice.
     */
    private void ensureDeterministicInvoiceThenCancel(PaymentRequest request, Instant now) {
        long fenceExpirySeconds = Math.max(1L, remainingExpirySecondsOrOne(request, now));
        byte[] preimage = Base64.getDecoder().decode(request.preimage());
        byte[] hash = HexFormat.of().parseHex(request.paymentHash());
        try {
            lightningNodePort.createInvoice(
                    request.amountSats(),
                    request.memo() == null ? "" : request.memo(),
                    preimage,
                    hash,
                    fenceExpirySeconds
            );
        } catch (Exception addError) {
            // Concurrent provision may have won the mint; continue with lookup/cancel.
            logger.info(
                    "Cancel fence AddInvoice raced for paymentHash={}: {}",
                    request.paymentHash(),
                    addError.getMessage()
            );
        }

        Optional<LightningNodeInvoice> afterEnsure = lightningNodePort.lookupInvoice(request.paymentHash());
        if (afterEnsure.isEmpty()) {
            throw new IllegalStateException(
                    "Cancel fence failed: deterministic invoice still absent for hash " + request.paymentHash()
            );
        }
        LightningNodeInvoice nodeInvoice = afterEnsure.get();
        if (nodeInvoice.state() == LightningInvoice.InvoiceState.SETTLED) {
            throw new SettledDuringCancelException(nodeInvoice);
        }
        if (nodeInvoice.state() != LightningInvoice.InvoiceState.CANCELED) {
            InvoiceCancelOutcome outcome = lightningNodePort.cancelInvoice(request.paymentHash());
            if (outcome == InvoiceCancelOutcome.ALREADY_SETTLED) {
                Optional<LightningNodeInvoice> settled = lightningNodePort.lookupInvoice(request.paymentHash());
                if (settled.isPresent()) {
                    throw new SettledDuringCancelException(settled.get());
                }
                throw new IllegalStateException(
                        "Cancel observed ALREADY_SETTLED without recoverable node invoice for hash "
                                + request.paymentHash()
                );
            }
        }

        Optional<LightningNodeInvoice> verified = lightningNodePort.lookupInvoice(request.paymentHash());
        if (verified.isPresent() && verified.get().state() == LightningInvoice.InvoiceState.SETTLED) {
            throw new SettledDuringCancelException(verified.get());
        }
        if (verified.isPresent() && verified.get().state() != LightningInvoice.InvoiceState.CANCELED) {
            throw new IllegalStateException(
                    "Cancel fence did not converge to CANCELED for hash " + request.paymentHash()
            );
        }
    }

    /**
     * @return true when settlement pipeline handled the request (skip CANCELLED finalize)
     */
    private boolean cancelAndHandleSettledRace(PaymentRequest request, String lockedBy) {
        InvoiceCancelOutcome outcome = lightningNodePort.cancelInvoice(request.paymentHash());
        if (outcome == InvoiceCancelOutcome.ALREADY_SETTLED) {
            Optional<LightningNodeInvoice> settled = lightningNodePort.lookupInvoice(request.paymentHash());
            if (settled.isPresent()) {
                settleOwnedFromNode(request, settled.get(), lockedBy);
                return true;
            }
            // Missing/ambiguous proof is retryable — never request-only PAID healing.
            throw new IllegalStateException(
                    "Cancel observed ALREADY_SETTLED without recoverable node invoice for hash "
                            + request.paymentHash()
            );
        }
        return false;
    }

    private void settleOwnedFromNode(PaymentRequest request, LightningNodeInvoice nodeInvoice, String lockedBy) {
        provisioningFinalizer.finalizeOpenOrSettled(
                request,
                nodeInvoice.paymentRequest(),
                LightningInvoice.InvoiceState.SETTLED,
                nodeInvoice.amountPaidSats() > 0 ? nodeInvoice.amountPaidSats() : request.amountSats(),
                remainingExpirySecondsOrOne(request, clock.instant()),
                lockedBy
        );
        logger.info(
                "Cancel saga observed SETTLED invoice; settled via pipeline for paymentHash={}",
                request.paymentHash()
        );
    }

    private void handleProvisionFailure(PaymentRequest request, String lockedBy, Exception e) {
        String message = truncate(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        logger.warn(
                "Provisioning failed for requestId={} paymentHash={} attempt={}: {}",
                request.id(),
                request.paymentHash(),
                request.provisionAttemptCount(),
                message
        );
        if (request.provisionAttemptCount() >= properties.getProvisionMaxAttempts()) {
            persistencePort.markProvisioningFailed(request.id(), message, lockedBy);
            logger.error(
                    "Provisioning exhausted for requestId={} paymentHash={}; status=FAILED",
                    request.id(),
                    request.paymentHash()
            );
            return;
        }
        persistencePort.scheduleProvisioningRetry(
                request.id(),
                message,
                nextAttemptAt(request.provisionAttemptCount()),
                lockedBy
        );
    }

    private void handleCancelFailure(PaymentRequest request, String lockedBy, Exception e) {
        if (e instanceof SettledDuringCancelException settled) {
            try {
                settleOwnedFromNode(request, settled.nodeInvoice(), lockedBy);
                return;
            } catch (Exception settleError) {
                e = settleError;
            }
        }
        String message = truncate(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        logger.warn(
                "Cancellation failed for requestId={} paymentHash={} attempt={}: {}",
                request.id(),
                request.paymentHash(),
                request.cancelAttemptCount(),
                message
        );
        persistencePort.scheduleCancelRetry(
                request.id(),
                message,
                nextAttemptAt(request.cancelAttemptCount()),
                lockedBy
        );
        if (request.cancelAttemptCount() >= properties.getCancelMaxAttempts()) {
            logger.error(
                    "Cancellation attempts exhausted for requestId={} paymentHash={}; remains CANCEL_PENDING for operator retry",
                    request.id(),
                    request.paymentHash()
            );
        }
    }

    private Instant nextAttemptAt(int attemptCount) {
        long exp = Math.min(
                properties.getBackoffMaxMs(),
                properties.getBackoffBaseMs() * (1L << Math.clamp(attemptCount - 1L, 0L, 20L))
        );
        return clock.instant().plusMillis(exp);
    }

    private long remainingExpirySeconds(PaymentRequest request, Instant now) {
        return Math.max(1L, Duration.between(now, request.expiresAt()).getSeconds());
    }

    private long remainingExpirySecondsOrOne(PaymentRequest request, Instant now) {
        long remaining = Duration.between(now, request.expiresAt()).getSeconds();
        return Math.max(1L, remaining);
    }

    private static String newClaimToken() {
        return LOCKED_BY_PREFIX + UUID.randomUUID();
    }

    private static String truncate(String message) {
        if (message.length() <= 2000) {
            return message;
        }
        return message.substring(0, 2000);
    }

    private static final class SettledDuringCancelException extends RuntimeException {
        private final transient LightningNodeInvoice nodeInvoice;

        private SettledDuringCancelException(LightningNodeInvoice nodeInvoice) {
            super("Invoice settled during cancel");
            this.nodeInvoice = nodeInvoice;
        }

        private LightningNodeInvoice nodeInvoice() {
            return nodeInvoice;
        }
    }
}
