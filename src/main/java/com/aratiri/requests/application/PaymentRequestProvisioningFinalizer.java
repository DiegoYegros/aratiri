package com.aratiri.requests.application;

import com.aratiri.infrastructure.messaging.outbox.OutboxWriter;
import com.aratiri.invoices.application.InvoiceSettledPublication;
import com.aratiri.invoices.application.InvoiceStateUpdate;
import com.aratiri.invoices.application.InvoiceStateUpdateResult;
import com.aratiri.invoices.application.event.InvoiceSettledEvent;
import com.aratiri.invoices.application.port.in.InvoiceSettlementPort;
import com.aratiri.invoices.application.port.out.LightningInvoicePersistencePort;
import com.aratiri.invoices.domain.LightningInvoice;
import com.aratiri.requests.application.port.out.PaymentRequestPersistencePort;
import com.aratiri.requests.domain.PaymentRequest;
import com.aratiri.requests.domain.PaymentRequestStatus;
import com.aratiri.webhooks.application.InvoiceCreatedWebhookFacts;
import com.aratiri.webhooks.application.WebhookEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Atomic local finalization for provisioning outcomes. Never performs LND RPCs.
 */
@Service
public class PaymentRequestProvisioningFinalizer {

    private static final Logger logger = LoggerFactory.getLogger(PaymentRequestProvisioningFinalizer.class);

    private final PaymentRequestPersistencePort persistencePort;
    private final LightningInvoicePersistencePort lightningInvoicePersistencePort;
    private final WebhookEventService webhookEventService;
    private final InvoiceSettlementPort invoiceSettlementPort;
    private final OutboxWriter outboxWriter;
    private final Clock clock;

    public PaymentRequestProvisioningFinalizer(
            PaymentRequestPersistencePort persistencePort,
            LightningInvoicePersistencePort lightningInvoicePersistencePort,
            WebhookEventService webhookEventService,
            InvoiceSettlementPort invoiceSettlementPort,
            OutboxWriter outboxWriter,
            Clock clock
    ) {
        this.persistencePort = persistencePort;
        this.lightningInvoicePersistencePort = lightningInvoicePersistencePort;
        this.webhookEventService = webhookEventService;
        this.invoiceSettlementPort = invoiceSettlementPort;
        this.outboxWriter = outboxWriter;
        this.clock = clock;
    }

    @Transactional
    public void finalizeOpenOrSettled(
            PaymentRequest request,
            String bolt11,
            LightningInvoice.InvoiceState nodeState,
            long amountPaidSats,
            long expirySeconds,
            String provisionLockedBy
    ) {
        if (nodeState == LightningInvoice.InvoiceState.SETTLED) {
            // SETTLED recovery: upsert-first so local invoice exists before settlement/outbox.
            LightningInvoice local = upsertLocalInvoice(request, bolt11, expirySeconds);
            InvoiceStateUpdateResult result = invoiceSettlementPort.recordInvoiceStateUpdate(new InvoiceStateUpdate(
                    bolt11,
                    request.paymentHash(),
                    InvoiceStateUpdate.State.SETTLED,
                    amountPaidSats
            ));
            result.settledPublication().ifPresentOrElse(
                    this::publishSettled,
                    // Local invoice already SETTLED (or settlement returned no publication):
                    // ensure deterministic invoice-settled outbox before/with request PAID.
                    () -> ensureSettledOutboxAndPaid(request, local)
            );
            logger.info(
                    "Provisioning recovered SETTLED LND invoice for paymentHash={}",
                    request.paymentHash()
            );
            return;
        }

        // OPEN path: fence local invoice materialization on an active provision claim.
        Optional<PaymentRequest> locked = persistencePort.findByIdForUpdate(request.id());
        if (locked.isEmpty()) {
            logSkipOpenFinalize(request.id(), "missing", null);
            return;
        }
        PaymentRequest claimed = locked.get();
        if (claimed.storedStatus() != PaymentRequestStatus.PROVISIONING
                || !provisionLockedBy.equals(claimed.provisionLockedBy())) {
            logSkipOpenFinalize(request.id(), claimed.storedStatus().name(), claimed.provisionLockedBy());
            return;
        }

        LightningInvoice local = upsertLocalInvoice(request, bolt11, expirySeconds);
        int updated = persistencePort.finalizeProvisioningOpen(
                request.id(), bolt11, local.id(), provisionLockedBy);
        if (updated == 0) {
            PaymentRequest after = persistencePort.findById(request.id()).orElse(null);
            if (after != null && (after.storedStatus() == PaymentRequestStatus.CANCEL_PENDING
                    || after.storedStatus() == PaymentRequestStatus.PAID
                    || after.storedStatus() == PaymentRequestStatus.CANCELLED
                    || after.storedStatus() == PaymentRequestStatus.FAILED)) {
                logger.info(
                        "Provisioning finalize raced with status={} for requestId={}",
                        after.storedStatus(),
                        request.id()
                );
            }
        }
    }

    private void logSkipOpenFinalize(String requestId, String status, String lockedBy) {
        logger.info(
                "Skipping OPEN provision finalize: claim inactive for requestId={} status={} lockedBy={}",
                requestId,
                status,
                lockedBy
        );
    }

    private void ensureSettledOutboxAndPaid(PaymentRequest request, LightningInvoice local) {
        LightningInvoice invoice = lightningInvoicePersistencePort.findByPaymentHash(request.paymentHash())
                .orElse(local);
        if (invoice.invoiceState() != LightningInvoice.InvoiceState.SETTLED) {
            throw new IllegalStateException(
                    "Settled node invoice is not SETTLED locally for hash " + request.paymentHash()
            );
        }
        InvoiceSettledEvent event = new InvoiceSettledEvent(
                invoice.userId(),
                invoice.amountSats(),
                invoice.paymentHash(),
                LocalDateTime.now(clock),
                invoice.memo()
        );
        outboxWriter.publishInvoiceSettled(invoice.id(), event);
        persistencePort.markPaidByPaymentHash(request.paymentHash(), clock.instant());
    }

    private void publishSettled(InvoiceSettledPublication publication) {
        outboxWriter.publishInvoiceSettled(publication.invoiceId(), publication.event());
    }

    private LightningInvoice upsertLocalInvoice(PaymentRequest request, String bolt11, long expirySeconds) {
        Optional<LightningInvoice> existing = lightningInvoicePersistencePort.findByPaymentHash(request.paymentHash());
        if (existing.isPresent()) {
            LightningInvoice invoice = existing.get();
            if (invoice.paymentRequest() == null || invoice.paymentRequest().isBlank()) {
                return lightningInvoicePersistencePort.save(new LightningInvoice(
                        invoice.id(),
                        invoice.userId(),
                        invoice.paymentHash(),
                        invoice.preimage(),
                        bolt11,
                        invoice.invoiceState(),
                        invoice.amountSats(),
                        invoice.createdAt(),
                        invoice.expiry(),
                        invoice.amountPaidSats(),
                        invoice.settledAt(),
                        invoice.memo(),
                        invoice.externalReference(),
                        invoice.metadata()
                ));
            }
            return invoice;
        }

        LightningInvoice created = new LightningInvoice(
                null,
                request.userId(),
                request.paymentHash(),
                request.preimage(),
                bolt11,
                LightningInvoice.InvoiceState.OPEN,
                request.amountSats(),
                LocalDateTime.now(clock),
                expirySeconds,
                0,
                null,
                request.memo(),
                null,
                null
        );
        LightningInvoice saved = lightningInvoicePersistencePort.save(created);
        webhookEventService.createInvoiceCreatedEvent(InvoiceCreatedWebhookFacts.from(saved));
        return saved;
    }
}
